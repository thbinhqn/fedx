/*
 * Copyright (C) 2018 Veritas Technologies LLC.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fluidops.fedx.optimizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.impl.EmptyBindingSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fluidops.fedx.EndpointManager;
import com.fluidops.fedx.FederationManager;
import com.fluidops.fedx.algebra.EmptyStatementPattern;
import com.fluidops.fedx.algebra.ExclusiveStatement;
import com.fluidops.fedx.algebra.StatementSource;
import com.fluidops.fedx.algebra.StatementSource.StatementSourceType;
import com.fluidops.fedx.algebra.StatementSourcePattern;
import com.fluidops.fedx.cache.Cache;
import com.fluidops.fedx.cache.Cache.StatementSourceAssurance;
import com.fluidops.fedx.cache.CacheEntry;
import com.fluidops.fedx.cache.CacheUtils;
import com.fluidops.fedx.endpoint.Endpoint;
import com.fluidops.fedx.evaluation.TripleSource;
import com.fluidops.fedx.evaluation.concurrent.ControlledWorkerScheduler;
import com.fluidops.fedx.evaluation.concurrent.ParallelExecutor;
import com.fluidops.fedx.evaluation.concurrent.ParallelTaskBase;
import com.fluidops.fedx.exception.ExceptionUtil;
import com.fluidops.fedx.exception.OptimizationException;
import com.fluidops.fedx.structures.QueryInfo;
import com.fluidops.fedx.structures.SubQuery;
import com.fluidops.fedx.util.QueryStringUtil;


/**
 * Perform source selection during optimization 
 * 
 * @author Andreas Schwarte
 *
 */
public class SourceSelection {

	private static final Logger log = LoggerFactory.getLogger(SourceSelection.class);
	
	protected final List<Endpoint> endpoints;
	protected final Cache cache;
	protected final QueryInfo queryInfo;
	
	
	public SourceSelection(List<Endpoint> endpoints, Cache cache, QueryInfo queryInfo) {
		this.endpoints = endpoints;
		this.cache = cache;
		this.queryInfo = queryInfo;
	}


	/**
	 * Map statements to their sources. Use synchronized access!
	 */
	protected Map<StatementPattern, List<StatementSource>> stmtToSources = new ConcurrentHashMap<StatementPattern, List<StatementSource>>();
	
	
	/**
	 * Perform source selection for the provided statements using cache or remote ASK queries.
	 * 
	 * Remote ASK queries are evaluated in parallel using the concurrency infrastructure of FedX. Note,
	 * that this method is blocking until every source is resolved.
	 * 
	 * The statement patterns are replaced by appropriate annotations in this optimization.
	 * 
	 * @param stmts
	 */
	public void doSourceSelection(List<StatementPattern> stmts) {
		
		List<CheckTaskPair> remoteCheckTasks = new ArrayList<CheckTaskPair>();
		
		// for each statement determine the relevant sources
		for (StatementPattern stmt : stmts) {
			

			// jump over the statement (e.g. if the same pattern is used in two union branches)
			if (stmtToSources.containsKey(stmt)) {
				continue;
			}
			

			stmtToSources.put(stmt, new ArrayList<StatementSource>());
			
			SubQuery q = new SubQuery(stmt);
				
			// check for each current federation member (cache or remote ASK)
			for (Endpoint e : endpoints) {
				StatementSourceAssurance a = cache.canProvideStatements(q, e);
				if (a==StatementSourceAssurance.HAS_LOCAL_STATEMENTS) {
					addSource(stmt, new StatementSource(e.getId(), StatementSourceType.LOCAL));
				} else if (a==StatementSourceAssurance.HAS_REMOTE_STATEMENTS) {
					addSource(stmt, new StatementSource(e.getId(), StatementSourceType.REMOTE));			
				} else if (a==StatementSourceAssurance.POSSIBLY_HAS_STATEMENTS) {					
					remoteCheckTasks.add( new CheckTaskPair(e, stmt));
				} else if (a==StatementSourceAssurance.NONE) { 
					// cannot provide any statements
					continue;
				}else {
					throw new IllegalStateException("Unexpected statement source assurance: " + a);
				}
			}
		}
		
		// if remote checks are necessary, execute them using the concurrency
		// infrastructure and block until everything is resolved
		if (remoteCheckTasks.size()>0) {
			SourceSelectionExecutorWithLatch.run(this, remoteCheckTasks, cache);
		}

		
		// iterate over input statements, BGP might be uses twice
		// resulting in the same entry in stmtToSources
		for (StatementPattern stmt : stmts)
		{
			
			List<StatementSource> sources = stmtToSources.get(stmt);
			
			// if more than one source -> StatementSourcePattern
			// exactly one source -> OwnedStatementSourcePattern
			// otherwise: No resource seems to provide results
			
			if (sources.size()>1) {
				StatementSourcePattern stmtNode = new StatementSourcePattern(stmt, queryInfo);
				for (StatementSource s : sources)
					stmtNode.addStatementSource(s);
				stmt.replaceWith(stmtNode);
			}
		
			else if (sources.size()==1) {
				stmt.replaceWith( new ExclusiveStatement(stmt, sources.get(0), queryInfo));
			}
			
			else {
				if (log.isDebugEnabled())
					log.debug("Statement " + QueryStringUtil.toString(stmt) + " does not produce any results at the provided sources, replacing node with EmptyStatementPattern." );
				stmt.replaceWith( new EmptyStatementPattern(stmt));
			}
		}		
	}
	
	/**
	 * Retrieve a set of relevant sources for this query.
	 * @return the relevant sources 
	 */
	public Set<Endpoint> getRelevantSources() {
		Set<Endpoint> endpoints = new HashSet<Endpoint>();
		for (List<StatementSource> sourceList : stmtToSources.values())
			for (StatementSource source : sourceList)
				endpoints.add( EndpointManager.getEndpointManager().getEndpoint(source.getEndpointID()));
		return endpoints;
	}	
	
	/**
	 * Add a source to the given statement in the map (synchronized through map)
	 * 
	 * @param stmt
	 * @param source
	 */
	protected void addSource(StatementPattern stmt, StatementSource source) {
		// The list for the stmt mapping is already initialized
		List<StatementSource> sources = stmtToSources.get(stmt);
		synchronized (sources) {
			sources.add(source);
		}
	}
	
	
	
	protected static class SourceSelectionExecutorWithLatch implements ParallelExecutor<BindingSet> {
		
		/**
		 * Execute the given list of tasks in parallel, and block the thread until
		 * all tasks are completed. Synchronization is achieved by means of a latch.
		 * Results are added to the map of the source selection instance. Errors 
		 * are reported as {@link OptimizationException} instances.
		 * 
		 * @param tasks
		 */
		public static void run(SourceSelection sourceSelection, List<CheckTaskPair> tasks, Cache cache) {
			new SourceSelectionExecutorWithLatch(sourceSelection).executeRemoteSourceSelection(tasks, cache);
		}		
		
		private final SourceSelection sourceSelection;
		private ControlledWorkerScheduler<BindingSet> scheduler = FederationManager.getInstance().getJoinScheduler();
		private CountDownLatch latch;
		private boolean finished=false;
		protected List<Exception> errors = new CopyOnWriteArrayList<>();
		

		private SourceSelectionExecutorWithLatch(SourceSelection sourceSelection) {
			this.sourceSelection = sourceSelection;
		}

		/**
		 * Execute the given list of tasks in parallel, and block the thread until
		 * all tasks are completed. Synchronization is achieved by means of a latch
		 * 
		 * @param tasks
		 */
		private void executeRemoteSourceSelection(List<CheckTaskPair> tasks, Cache cache) {
			if (tasks.size()==0)
				return;
			
			latch = new CountDownLatch(tasks.size());
			for (CheckTaskPair task : tasks)
				scheduler.schedule( new ParallelCheckTask(task.e, task.t, this) );
			
			try	{
				boolean completed = latch.await(getQueryInfo().getMaxRemainingTimeMS(), TimeUnit.MILLISECONDS);
				if (!completed) {
					throw new OptimizationException("Source selection has run into a timeout");
				}
			} catch (InterruptedException e) {
				log.debug("Error during source selection. Thread got interrupted.");
				errors.add(e);
			}

			finished = true;
			
			// check for errors:
			if (errors.size()>0) {
				StringBuilder sb = new StringBuilder();
				sb.append(
						errors.size() + " errors were reported while optimizing query " + getQueryInfo().getQueryID());

				for (Exception e : errors)
					sb.append("\n" + ExceptionUtil.getExceptionString("Error occured", e));

				log.debug(sb.toString());

				Exception ex = errors.get(0);
				errors.clear();
				if (ex instanceof OptimizationException)
					throw (OptimizationException)ex;
				
				throw new OptimizationException(ex.getMessage(), ex);
			}
		}

		@Override
		public void run() { /* not needed */ }

		@Override
		public void addResult(CloseableIteration<BindingSet, QueryEvaluationException> res)	{
			latch.countDown();
		}

		@Override
		public void toss(Exception e) {
			latch.countDown();
			errors.add(e);
			getQueryInfo().abort();
		}

		@Override
		public void done()	{ /* not needed */ }

		@Override
		public boolean isFinished()	{
			return finished;
		}

		@Override
		public QueryInfo getQueryInfo() {
			return sourceSelection.queryInfo;
		}
	}
	
	
	protected class CheckTaskPair {
		public final Endpoint e;
		public final StatementPattern t;
		public CheckTaskPair(Endpoint e, StatementPattern t){
			this.e = e;
			this.t = t;
		}		
	}
	
	
	/**
	 * Task for sending an ASK request to the endpoints (for source selection)
	 * 
	 * @author Andreas Schwarte
	 */
	protected static class ParallelCheckTask extends ParallelTaskBase<BindingSet> {

		protected final Endpoint endpoint;
		protected final StatementPattern stmt;
		protected final SourceSelectionExecutorWithLatch control;
		
		public ParallelCheckTask(Endpoint endpoint, StatementPattern stmt, SourceSelectionExecutorWithLatch control) {
			this.endpoint = endpoint;
			this.stmt = stmt;
			this.control = control;
		}

		
		@Override
		public CloseableIteration<BindingSet, QueryEvaluationException> performTask() throws Exception {
			try {
				TripleSource t = endpoint.getTripleSource();
				boolean hasResults = false;
				hasResults = t.hasStatements(stmt, EmptyBindingSet.getInstance());

				SourceSelection sourceSelection = control.sourceSelection;
				CacheEntry entry = CacheUtils.createCacheEntry(endpoint, hasResults);
				sourceSelection.cache.updateEntry( new SubQuery(stmt), entry);

				if (hasResults)
					sourceSelection.addSource(stmt, new StatementSource(endpoint.getId(), StatementSourceType.REMOTE));
				
				return null;
			} catch (Exception e) {
				throw new OptimizationException("Error checking results for endpoint " + endpoint.getId() + ": " + e.getMessage(), e);
			}
		}

		@Override
		public ParallelExecutor<BindingSet> getControl() {
			return control;
		}

		@Override
		public void cancel() {
			control.latch.countDown();
			super.cancel();
		}
	}
	
		
}





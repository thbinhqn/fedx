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
package com.fluidops.fedx.evaluation;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.impl.EmptyBindingSet;
import org.eclipse.rdf4j.repository.RepositoryException;

import com.fluidops.fedx.algebra.ExclusiveGroup;
import com.fluidops.fedx.algebra.FilterValueExpr;
import com.fluidops.fedx.structures.QueryType;


/**
 * Interface for implementations of triple sources. Particular implementations define
 * how to evaluate the expression on the endpoint. Different implementations might
 * be necessary depending on the underlying repository.
 * 
 * @author Andreas Schwarte
 *
 * @see SparqlTripleSource
 * @see SailTripleSource
 */
public interface TripleSource {

	
	/**
	 * Evaluate the prepared query in its internal representation on the provided endpoint.
	 * 
	 * @param preparedQuery
	 * 			a prepared query to evaluate
	 * @param bindings
	 * 			the bindings to use
	 * @param filterExpr
	 * 			the filter expression to apply or null if there is no filter or if it is evaluated already
	 * 
	 * @return
	 * 		the resulting iteration
	 *  
	 * @throws RepositoryException
	 * @throws MalformedQueryException
	 * @throws QueryEvaluationException
	 */
	public CloseableIteration<BindingSet, QueryEvaluationException> getStatements(TupleExpr preparedQuery,
			final BindingSet bindings, FilterValueExpr filterExpr)
			throws RepositoryException, MalformedQueryException, QueryEvaluationException;

	
	/**
	 * Evaluate the prepared query (SPARQL query as String) on the provided endpoint.
	 * 
	 * @param preparedQuery
	 * 			a prepared query to evaluate (SPARQL query as String)
	 * @param bindings
	 * 			the bindings to use
	 * @param filterExpr
	 * 			the filter expression to apply or null if there is no filter or if it is evaluated already
	 * 
	 * @return
	 * 		the resulting iteration
	 *  
	 * @throws RepositoryException
	 * @throws MalformedQueryException
	 * @throws QueryEvaluationException
	 */
	public CloseableIteration<BindingSet, QueryEvaluationException> getStatements(String preparedQuery, final BindingSet bindings, FilterValueExpr filterExpr) throws RepositoryException, MalformedQueryException, QueryEvaluationException;

	/**
	 * Evaluate a given SPARQL query of the provided query type at the given source.
	 * 
	 * @param preparedQuery
	 * @param queryType
	 * @return the statements
	 * @throws RepositoryException
	 * @throws MalformedQueryException
	 * @throws QueryEvaluationException
	 */
	public CloseableIteration<BindingSet, QueryEvaluationException> getStatements(String preparedQuery, QueryType queryType) throws RepositoryException, MalformedQueryException, QueryEvaluationException;
	
	/**
	 * Evaluate the query expression on the provided endpoint.
	 * 
	 * @param stmt
	 * 			the stmt expression to evaluate
	 * @param bindings
	 * 			the bindings to use
	 * @param filterExpr
	 * 			the filter expression to apply or null if there is no filter or if it is evaluated already
	 * 
	 * @return
	 * 		the resulting iteration
	 * 	
	 * @throws RepositoryException
	 * @throws MalformedQueryException
	 * @throws QueryEvaluationException
	 */
	public CloseableIteration<BindingSet, QueryEvaluationException> getStatements(StatementPattern stmt, final BindingSet bindings, FilterValueExpr filterExpr) throws RepositoryException, MalformedQueryException, QueryEvaluationException;

	
	/**
	 * Return the statements matching the given pattern as a {@link Statement} iteration.
	 * 
	 * @param subj
	 * @param pred
	 * @param obj
	 * @param contexts
	 * 
	 * @return
	 * 			the resulting itereation
	 * 
	 * @throws RepositoryException
	 * @throws MalformedQueryException
	 * @throws QueryEvaluationException
	 */
	public CloseableIteration<Statement, QueryEvaluationException> getStatements(
			Resource subj, IRI pred, Value obj, Resource... contexts)
			throws RepositoryException, MalformedQueryException, QueryEvaluationException;
	
	
	/**
	 * Check if the provided statement can return results.
	 * 
	 * @param stmt
	 * @param bindings
	 * 			a binding set. in case no bindings are present, an {@link EmptyBindingSet} can be used (i.e. never null)
	 * 
	 * @return whether the source can return results
	 * @throws RepositoryException
	 * @throws MalformedQueryException
	 * @throws QueryEvaluationException
	 */
	public boolean hasStatements(StatementPattern stmt, BindingSet bindings)
			throws RepositoryException, MalformedQueryException, QueryEvaluationException;
	
	/**
	 * Check if the repository can return results for the given triple pattern represented
	 * by subj, pred and obj
	 * 
	 * @param subj
	 * @param pred
	 * @param obj
	 * @param contexts
	 * @return whether the source can provide results
	 * @throws RepositoryException
	 */
	public boolean hasStatements(Resource subj, IRI pred, Value obj, Resource... contexts)
			throws RepositoryException;
	
	/**
	 * Check if the repository can return results for the given {@link ExclusiveGroup},
	 * i.e. a list of Statements
	 * 
	 * @param bindings
	 * @return whether the repository can return results
	 * @throws RepositoryException
	 */
	public boolean hasStatements(ExclusiveGroup group, BindingSet bindings)
			throws RepositoryException, MalformedQueryException, QueryEvaluationException;
	
	
	/**
	 * 
	 * @return
	 * 		true if a prepared query is to be used preferrably, false otherwise
	 */
	public boolean usePreparedQuery();
	
	
}

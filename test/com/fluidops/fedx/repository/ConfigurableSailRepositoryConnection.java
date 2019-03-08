package com.fluidops.fedx.repository;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailBooleanQuery;
import org.eclipse.rdf4j.repository.sail.SailGraphQuery;
import org.eclipse.rdf4j.repository.sail.SailQuery;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailTupleQuery;
import org.eclipse.rdf4j.sail.SailConnection;

import com.fluidops.fedx.repository.ConfigurableSailRepositoryFactory.FailingRepositoryException;

/**
 * Specialized {@link SailRepositoryConnection} that can be used with
 * {@link ConfigurableSailRepository}
 * 
 * @author Andreas Schwarte
 *
 */
public class ConfigurableSailRepositoryConnection extends SailRepositoryConnection {

	private final ConfigurableSailRepository rep;
	
	protected ConfigurableSailRepositoryConnection(ConfigurableSailRepository repository,
			SailConnection sailConnection) {
		super(repository, sailConnection);
		this.rep = repository;
	}
	
	@Override
	public void add(Statement st, Resource... contexts)
			throws RepositoryException {
		checkFail(true);
		super.add(st, contexts);
	}

	@Override
	public void add(Iterable<? extends Statement> arg0,
			Resource... arg1) throws RepositoryException {
		checkFail(true);
		super.add(arg0, arg1);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void add(Resource subject, org.eclipse.rdf4j.model.URI predicate, Value object,
			Resource... contexts) throws RepositoryException {
		checkFail(true);
		super.add(subject, predicate, object, contexts);
	}
	
	@Override
	public boolean hasStatement(Resource subj, IRI pred, Value obj, boolean includeInferred, Resource... contexts)
			throws RepositoryException {
		checkFail(false);
		return super.hasStatement(subj, pred, obj, includeInferred, contexts);
	}

	@Override
	public RepositoryResult<Statement> getStatements(Resource subj, IRI pred, Value obj, boolean includeInferred,
			Resource... contexts) throws RepositoryException {
		checkFail(false);
		return super.getStatements(subj, pred, obj, includeInferred, contexts);
	}

	@Override
	public void add(Resource subject, IRI predicate, Value object, Resource... contexts)
			throws RepositoryException {
		checkFail(true);
		super.add(subject, predicate, object, contexts);
	}

	@Override
	public SailBooleanQuery prepareBooleanQuery(QueryLanguage ql,
			String queryString, String baseURI)
			throws MalformedQueryException
	{
		checkFail(false);
		return super.prepareBooleanQuery(ql, queryString, baseURI);
	}

	@Override
	public SailGraphQuery prepareGraphQuery(QueryLanguage ql,
			String queryString, String baseURI)
			throws MalformedQueryException
	{
		checkFail(false);
		return super.prepareGraphQuery(ql, queryString, baseURI);
	}

	@Override
	public SailQuery prepareQuery(QueryLanguage ql, String queryString,
			String baseURI) throws MalformedQueryException
	{
		checkFail(false);
		return super.prepareQuery(ql, queryString, baseURI);
	}

	@Override
	public SailTupleQuery prepareTupleQuery(QueryLanguage ql,
			String queryString, String baseURI)
			throws MalformedQueryException
	{
		checkFail(false);
		return super.prepareTupleQuery(ql, queryString, baseURI);
	}

	@Override
	public Update prepareUpdate(QueryLanguage ql, String update,
			String baseURI) throws RepositoryException,
			MalformedQueryException
	{
		checkFail(true);
		return super.prepareUpdate(ql, update, baseURI);
	}


	private void checkFail(boolean isWrite) throws FailingRepositoryException {
		int _operationsCount = 0;
		if (rep.failAfter >= 0) {
			_operationsCount = rep.operationsCount.incrementAndGet();
		} else {
			rep.operationsCount.set(0);
		}

		if (isWrite && !rep.writable)
			throw new FailingRepositoryException("Operation failed, not writable");
		if (rep.failAfter != -1 && _operationsCount > rep.failAfter)
			throw new FailingRepositoryException("Operation failed");
	}
}
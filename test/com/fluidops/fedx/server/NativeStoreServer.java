package com.fluidops.fedx.server;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.sail.nativerdf.NativeStoreExt;
import org.junit.rules.TemporaryFolder;

import com.fluidops.fedx.repository.ConfigurableSailRepository;
import com.fluidops.fedx.structures.Endpoint;
import com.fluidops.fedx.structures.Endpoint.EndpointClassification;
import com.fluidops.fedx.structures.Endpoint.EndpointType;
import com.fluidops.fedx.util.EndpointFactory;

public class NativeStoreServer extends TemporaryFolder implements Server {

	private List<Repository> repositories = new ArrayList<>();
	
	@Override
	public void initialize(int nRepositories) throws Exception {
		try {
			this.before();
		} catch (Throwable e) {
			throw new Exception(e);
		}
		File baseDir = newFolder();
		for (int i=1; i<=nRepositories; i++) {
			ConfigurableSailRepository repo = new ConfigurableSailRepository(
					new NativeStoreExt(new File(baseDir, "endpoint" + i)), true);
			repo.initialize();
			repositories.add(repo);
			repo.shutDown();
		}
	}
	
	@Override
	public void shutdown() throws Exception {
		try {
			this.after();
		} catch (Throwable e) {
			throw new Exception(e);
		}
	}

	@Override
	public Endpoint loadEndpoint(int i) throws Exception {
		Endpoint e = EndpointFactory.loadEndpoint("endpoint" + i, repositories.get(i - 1));
		e.setEndpointClassification(EndpointClassification.Local);
		e.setType(EndpointType.NativeStore);
		return e;
	}

	@Override
	public ConfigurableSailRepository getRepository(int i) {
		return (ConfigurableSailRepository) repositories.get(i - 1);
	}

}

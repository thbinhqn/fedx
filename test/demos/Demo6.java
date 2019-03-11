package demos;

import java.util.Collections;

import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;

import com.fluidops.fedx.FedXFactory;
import com.fluidops.fedx.QueryManager;
import com.fluidops.fedx.structures.Endpoint;

public class Demo6 {

	
	public static void main(String[] args) throws Exception {
		
		// the fedx config implicitly defines a dataConfig
		String fedxConfig = "examples/fedxConfig-withPrefixDecl.prop";
		Repository repo = FedXFactory.initializeFederation(fedxConfig, Collections.<Endpoint>emptyList());
		
		String q = "SELECT ?President ?Party WHERE {\n"
			+ "?President rdf:type dbpedia:President .\n"
			+ "?President dbpedia:party ?Party . }";
		
		TupleQuery query = QueryManager.prepareTupleQuery(q);
		try (TupleQueryResult res = query.evaluate()) {
		
			while (res.hasNext()) {
				System.out.println(res.next());
			}
		}
		
		repo.shutDown();
		System.out.println("Done.");
		System.exit(0);
		
	}
}

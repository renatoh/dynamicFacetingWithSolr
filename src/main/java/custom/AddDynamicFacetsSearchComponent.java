package custom;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;

import java.io.IOException;
import java.util.List;

import static custom.CommonParams.FACET_FIELD_NAME;
import static org.apache.commons.collections.CollectionUtils.isEmpty;

/***
 * Here we are doing a sub-query to, faceting on FACET_FIELD_NAME to determine the frequency of the fields.
 * Fields which appear frequently enough, will be added as a facet to the main query.
 */
public class AddDynamicFacetsSearchComponent extends SearchComponent
{
	//could be a property or even passed in on the request by the client so that it can be easily adjusted
	private final static double FACET_THRESHOLD = 0.3;
   

	@Override
	public void prepare(final ResponseBuilder responseBuilder) throws IOException
	{
		SolrParams params = responseBuilder.req.getParams();
	                                     
		SolrClient solrClient = new EmbeddedSolrServer(responseBuilder.req.getCore());

		ModifiableSolrParams subQueryParams = new ModifiableSolrParams(params);
		String isSubQuery = params.get("isSubQuery");
		
		if("true".equals(isSubQuery))//needed to prevent doing a sub-query of the sub-query and ending up in a infinite loop
		{                    
			return;	
		}

		subQueryParams.set("rows",0); //we are not interested in the actual results
		subQueryParams.set("facet",true);

		subQueryParams.set("facet.field", FACET_FIELD_NAME);
		subQueryParams.set("isSubQuery", "true"); //mark query so that we can skip the sub-query of a sub-query

		try
		{
			QueryResponse response = solrClient.query(subQueryParams);

			FacetField pimFields = response.getFacetField(FACET_FIELD_NAME);

			
			if(isEmpty(response.getResults()) || pimFields == null || pimFields.getValues() != null)
			{
				return;
			}
			List<FacetField.Count> values = pimFields.getValues();

			ModifiableSolrParams newParams = new ModifiableSolrParams(params);
			long numFound = response.getResults().getNumFound();

			addFacets(values, newParams, numFound);
			
			newParams.set("facet", true);
			newParams.set("facet.mincount",1);
			responseBuilder.req.setParams(newParams);

		}
		catch (SolrServerException e)
		{
			throw new RuntimeException(e);
		}
	}

	private static void addFacets(final List<FacetField.Count> values, final ModifiableSolrParams newParams, final long numFound)
	{
		for (final FacetField.Count value : values)
		{
			//here we are calculating how often a field appears in the results relative do the number of total search results
			if(value.getCount() < FACET_THRESHOLD * numFound)//assumes that facet.sort=count (the default) is used
			{
				break;
			}
			newParams.add("facet.field", value.getName());				
		}
	}

	@Override
	public void process(final ResponseBuilder responseBuilder)
	{

	}

	@Override
	public String getDescription()
	{
		return null;
	}
}

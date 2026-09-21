package custom;

import org.apache.lucene.search.Query;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.request.SimpleFacets;
import org.apache.solr.search.DocSet;
import org.apache.solr.search.QParser;
import org.apache.solr.search.SyntaxError;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static custom.CommonParams.FACET_FIELD_NAME;

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

		ModifiableSolrParams facetParams = new ModifiableSolrParams();
		facetParams.set("facet", true);
		facetParams.set("facet.field", FACET_FIELD_NAME);

		DocSet docSet;
		NamedList<Object> facetCounts;
		try
		{
			docSet = responseBuilder.req.getSearcher().getDocSet(buildQueries(responseBuilder, params));
			facetCounts = new SimpleFacets(responseBuilder.req, docSet, facetParams, responseBuilder).getFacetFieldCounts();
		}
		catch (SyntaxError e)
		{
			throw new IOException(e);
		}

		@SuppressWarnings("unchecked")
		NamedList<Integer> fieldCounts = (NamedList<Integer>) facetCounts.get(FACET_FIELD_NAME);

		long numFound = docSet.size();

		if (numFound == 0 || fieldCounts == null || fieldCounts.size() == 0)
		{
			return;
		}

		ModifiableSolrParams newParams = new ModifiableSolrParams(params);
		addFacets(fieldCounts, newParams, numFound);
		newParams.set("facet", true);
		newParams.set("facet.mincount", 1);
		responseBuilder.req.setParams(newParams);
	}

	private static List<Query> buildQueries(final ResponseBuilder responseBuilder, final SolrParams params)
			throws SyntaxError
	{
		List<Query> queries = new ArrayList<>();
		queries.add(QParser.getParser(params.get("q", "*:*"), null, responseBuilder.req).getQuery());
		String[] fqs = params.getParams("fq");
		if (fqs != null)
		{
			for (String fq : fqs)
			{
				queries.add(QParser.getParser(fq, null, responseBuilder.req).getQuery());
			}
		}
		return queries;
	}

	private static void addFacets(final NamedList<Integer> fieldCounts, final ModifiableSolrParams newParams, final long numFound)
	{
		for (final Map.Entry<String, Integer> entry : fieldCounts)
		{
			//here we are calculating how often a field appears in the results relative do the number of total search results
			if (entry.getValue() < FACET_THRESHOLD * numFound) //assumes that facet.sort=count (the default) is used
			{
				break;
			}
			newParams.add("facet.field", entry.getKey());
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

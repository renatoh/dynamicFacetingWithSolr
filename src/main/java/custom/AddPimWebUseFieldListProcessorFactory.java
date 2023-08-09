
package custom;

import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.update.AddUpdateCommand;
import org.apache.solr.update.processor.UpdateRequestProcessor;
import org.apache.solr.update.processor.UpdateRequestProcessorFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static custom.CommonParams.FACET_FIELD_NAME;


/***
 *Here we want to take all the fields from the SolrInputDocument starting with df_ and create a new field called dynamic_faceting_string_mv
 * with all the field-names in it. The content of this field could look like this:
 * [
 * 	"df_height_double",
 * 	"df_weight_double",
 * 	"df_brand_string_de",
 * 	"df_price_double",
 * 	"df_categories_string_mv",
 * 	"df_powerconsumption_double","
 * 	 etc.
 * 	 ]
 * 	 This field is then used in AddDynamicFacetsSearchComponent.
 */
public class AddPimWebUseFieldListProcessorFactory extends UpdateRequestProcessorFactory
{
	public static final String PIM_WEB_USE_PREFIX = "df_";

	public UpdateRequestProcessor getInstance(SolrQueryRequest req,
											  SolrQueryResponse rsp,
											  UpdateRequestProcessor next)
	{
		return new UpdateRequestProcessor(next)
		{
			@Override
			public void processAdd(final AddUpdateCommand cmd) throws IOException
			{
				List<String> technicalAttribute = new ArrayList<>();
				for (final String name : cmd.getSolrInputDocument().getFieldNames())
				{                         	
					if (name.contains(PIM_WEB_USE_PREFIX))
					{
						technicalAttribute.add(name);
						
					}
				}
				cmd.getSolrInputDocument().addField(FACET_FIELD_NAME, technicalAttribute);
				super.processAdd(cmd);
			}
		};
	}
}




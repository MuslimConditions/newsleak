package uhh_lt.newsleak.reader;

import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.component.CasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.ExternalResource;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.get.GetField;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.search.sort.SortParseElement;

import de.unihd.dbs.uima.types.heideltime.Dct;
import uhh_lt.newsleak.resources.ElasticsearchResource;
import uhh_lt.newsleak.resources.HooverResource;
import uhh_lt.newsleak.types.Metadata;
import uhh_lt.newsleak.writer.ElasticsearchDocumentWriter;

public class HooverElasticsearchReader extends CasCollectionReader_ImplBase {

	private Logger logger;

	public static final String RESOURCE_HOOVER = "hooverResource";
	@ExternalResource(key = RESOURCE_HOOVER)
	private HooverResource hooverResource;

	public static final String PARAM_DEFAULT_LANG = "defaultLanguage";
	@ConfigurationParameter(name=PARAM_DEFAULT_LANG, mandatory=false, defaultValue="en")
	private String defaultLanguage;

	public static final String PARAM_DEBUG_MAX_DOCS = "maxRecords";
	@ConfigurationParameter(name=PARAM_DEBUG_MAX_DOCS, mandatory=false)
	private Integer maxRecords = Integer.MAX_VALUE;

	private TransportClient client;
	private String esIndex;

	private int totalRecords = 0;
	private int currentRecord = 0;

	private ArrayList<String> totalIdList;
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	SimpleDateFormat dateJson = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
	Pattern emailPattern = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+");

	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);

		logger = context.getLogger();
		client = hooverResource.getClient();
		esIndex = hooverResource.getIndex();

		try {

			XContentBuilder builder = XContentFactory.jsonBuilder()
					.startObject()
					.field("match_all")
					.startObject()
					.endObject().endObject();
			totalIdList = new ArrayList<String>();
			SearchResponse scrollResp = client.prepareSearch(esIndex)
					.addSort(SortParseElement.DOC_FIELD_NAME, SortOrder.ASC)
					.setScroll(new TimeValue(60000))
					.setQuery(builder)
					.setSize(10000).execute().actionGet(); 
			while (true) {
				for (SearchHit hit : scrollResp.getHits().getHits()) {
					totalIdList.add(hit.getId());
				}
				scrollResp = client.prepareSearchScroll(scrollResp.getScrollId()).setScroll(new TimeValue(60000)).execute().actionGet();
				//Break condition: No hits are returned
				if (scrollResp.getHits().getHits().length == 0) {
					break;
				}
			}

			totalRecords = totalIdList.size();
			logger.log(Level.INFO, "Found " + totalRecords + " in index " + esIndex);

		} catch (IOException e) {
			throw new ResourceInitializationException(e);
		}
	}


	public void getNext(CAS cas) throws IOException, CollectionException {

		currentRecord++;

		JCas jcas;
		try {
			jcas = cas.getJCas();
		} catch (CASException e) {
			throw new CollectionException(e);
		}

		String docId = totalIdList.get(currentRecord - 1);
		GetField field;
		GetResponse response = client
				.prepareGet(esIndex, "doc", docId)
				.setFields("attachments", "date", "date-created", "content-type", 
						"filetype", "from", "to", "in-reply-to", "subject", "text",
						"filename", "path")
				.get();

		String docText = "";
		// email subject
		field = response.getField("subject");
		if (field != null) {
			docText += (String) field.getValue() + "\n\n";
		}
		field = response.getField("text");
		if (field != null) {
			docText += (String) field.getValue();
		}
		jcas.setDocumentText(docText);

		// set document metadata
		Metadata metaCas = new Metadata(jcas);
		metaCas.setDocId(docId);

		// date
		GetField date = response.getField("date");
		if (date == null) {
			date = response.getField("date-created");
		}
		String docDate;
		try {
			docDate = date != null ? dateFormat.format(dateJson.parse((String) date.getValue())) : "1900-01-01";
			metaCas.setTimestamp(docDate);

			// heideltime
			Dct dct = new Dct(jcas);
			dct.setValue(docDate);
			dct.addToIndexes();

		} catch (ParseException e) {
			e.printStackTrace();
		}

		metaCas.addToIndexes();

		// write external metadata
		ArrayList<List<String>> metadata = new ArrayList<List<String>>();
		
		// attachments
		field = response.getField("attachments");
		if (field != null)
			metadata.add(createTextMetadata(docId, "attachments", ((Boolean) field.getValue()).toString()));
		// content-type
		field = response.getField("content-type");
		if (field != null)
			metadata.add(createTextMetadata(docId, "content-type", (String) field.getValue()));
		// file-type
		field = response.getField("filetype");
		if (field != null)
			metadata.add(createTextMetadata(docId, "filetype", (String) field.getValue()));
		// from
		field = response.getField("from");
		if (field != null) {
			for (String email : extractEmail((String) field.getValue())) {
				metadata.add(createTextMetadata(docId, "from", email));
			}
		}
		// to
		field = response.getField("to");
		if (field != null) {
			for (Object toList : field.getValues()) {
				for (String email : extractEmail((String) toList)) {
					metadata.add(createTextMetadata(docId, "to", email));
				}
			}
		}
		hooverResource.appendMetadata(metadata);

	}

	private ArrayList<String> extractEmail(String s) {
		ArrayList<String> emails = new ArrayList<String>();
		Matcher m = emailPattern.matcher(s);
		while (m.find()) {
			emails.add(m.group());
		}
		return emails;
	}


	private ArrayList<String> createTextMetadata(String docId, String key, String value) {
		ArrayList<String> meta = new ArrayList<String>();
		meta.add(docId);
		meta.add(key);
		meta.add(value.replaceAll("\\r|\\n", " "));
		meta.add("text");
		return meta;
	}


	public Progress[] getProgress() {
		return new Progress[] {
				new ProgressImpl(
						Long.valueOf(currentRecord).intValue(),
						Long.valueOf(totalRecords).intValue(),
						Progress.ENTITIES
						)
		};
	}

	public boolean hasNext() throws IOException, CollectionException {
		if (currentRecord > maxRecords) return false;
		return currentRecord < totalRecords ? true : false;
	}

}

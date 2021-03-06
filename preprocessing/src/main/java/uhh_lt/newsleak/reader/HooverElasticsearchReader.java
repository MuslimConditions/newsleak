package uhh_lt.newsleak.reader;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
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
import uhh_lt.newsleak.resources.HooverResource;
import uhh_lt.newsleak.resources.MetadataResource;
import uhh_lt.newsleak.types.Metadata;

public class HooverElasticsearchReader extends CasCollectionReader_ImplBase {

	private Logger logger;

	public static final String RESOURCE_HOOVER = "hooverResource";
	@ExternalResource(key = RESOURCE_HOOVER)
	private HooverResource hooverResource;
	
	public static final String RESOURCE_METADATA = "metadataResource";
	@ExternalResource(key = RESOURCE_METADATA)
	private MetadataResource metadataResource;

	public static final String PARAM_DEBUG_MAX_DOCS = "maxRecords";
	@ConfigurationParameter(name = PARAM_DEBUG_MAX_DOCS, mandatory = false)
	private Integer maxRecords = Integer.MAX_VALUE;
	
	public static final String PARAM_MAX_DOC_LENGTH = "maxDocumentLength";
	@ConfigurationParameter(name = PARAM_MAX_DOC_LENGTH, mandatory = false)
	private Integer maxDocumentLength = Integer.MAX_VALUE; // 1500 * 10000 = 10000 norm pages
	
	private TransportClient client;
	private String esIndex;
	private String clientUrl;

	private int totalRecords = 0;
	private int currentRecord = 0;

	private ArrayList<String> totalIdList;
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	SimpleDateFormat dateCreated = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	SimpleDateFormat dateJson = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
	Pattern emailPattern = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+");

	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);

		logger = context.getLogger();
		client = hooverResource.getClient();
		esIndex = hooverResource.getIndex();

		clientUrl = hooverResource.getClientUrl();
		
		try {

			XContentBuilder builder = XContentFactory.jsonBuilder()
					.startObject()
					.field("match_all")
					.startObject()
					.endObject().endObject();
			totalIdList = new ArrayList<String>();
			logger.log(Level.INFO, "Start scroll request on " + esIndex);
			SearchRequestBuilder sb = client.prepareSearch(esIndex)
					.addSort(SortParseElement.DOC_FIELD_NAME, SortOrder.ASC)
					.setScroll(TimeValue.timeValueSeconds(15L))
					.setQuery(builder)
					.setFetchSource(false)
					.setSize(10000); 
			System.out.println(sb.toString());
			SearchResponse scrollResp = sb.execute().actionGet();
			while (true) {
				logger.log(Level.INFO, "Continuing scroll request on " + esIndex);
				for (SearchHit hit : scrollResp.getHits().getHits()) {
					totalIdList.add(hit.getId());
				}
				scrollResp = client.prepareSearchScroll(scrollResp.getScrollId()).setScroll(TimeValue.timeValueSeconds(15L)).execute().actionGet();
				//Break condition: No hits are returned
				int nHits = scrollResp.getHits().getHits().length;
				if (nHits == 0) {
					break;
				}
				logger.log(Level.INFO, "Added hits " + nHits);
			}
			
			// clear scroll request
			ClearScrollRequest request = new ClearScrollRequest(); 
			request.addScrollId(scrollResp.getScrollId());
			client.clearScroll(request);
			
			if (maxRecords > 0 && maxRecords < totalIdList.size()) {
				totalIdList = new ArrayList<String>(totalIdList.subList(0, maxRecords));
			}

			totalRecords = totalIdList.size();
			logger.log(Level.INFO, "Found " + totalRecords + " in index " + esIndex);

		} catch (IOException e) {
			throw new ResourceInitializationException(e);
		}
	}


	public void getNext(CAS cas) throws IOException, CollectionException {
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
		
		// put email header information in main text
		field = response.getField("from");
		if (field != null) {
			String fromText = ((String) field.getValue()).trim();
			docText += "From: " + fromText.replaceAll("<", "[").replaceAll(">", "]") + "\n";
		}
		field = response.getField("to");
		if (field != null) {
			String toText = ((String) field.getValue()).trim();
			docText += "To: " + toText.replaceAll("<", "[").replaceAll(">", "]") + "\n";
		}
		field = response.getField("subject");
		if (field != null) {
			docText += "Subject: " + ((String) field.getValue()).trim() + "\n";
		}
		if (!docText.isEmpty()) {
			docText += "\n-- \n\n";
		}
		
		// add main text
		field = response.getField("text");
		if (field != null) {
			String completeText = ((String) field.getValue()).trim();
			docText += completeText.substring(0, Math.min(completeText.length(), maxDocumentLength));
		}
		jcas.setDocumentText(docText);

		// set document metadata
		Metadata metaCas = new Metadata(jcas);
		String docIdHash = "" + docId.hashCode();
		metaCas.setDocId(docIdHash);

		// date
		String docDate = "1900-01-01";
		Date dateField = null;
		Date dateCreatedField = null;
		try {
			
			GetField date = response.getField("date");
			if (date != null) {
				// docDate = dateFormat.format();
				dateField = dateCreated.parse((String) date.getValue());
			}
			
			date = response.getField("date-created");
			if (date != null) {
				// docDate = dateFormat.format() ;
				dateCreatedField = dateJson.parse((String) date.getValue());
			}
			
			if (dateField != null && dateCreatedField != null) {
				docDate = dateField.before(dateCreatedField) ? dateFormat.format(dateCreatedField) : dateFormat.format(dateField);
			} else {
				if (dateField != null) {
					docDate = dateFormat.format(dateField);
				}
				if (dateCreatedField != null) {
					docDate = dateFormat.format(dateCreatedField);
				}
			}

		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		metaCas.setTimestamp(docDate);

		// heideltime
		Dct dct = new Dct(jcas);
		dct.setValue(docDate);
		dct.addToIndexes();

		metaCas.addToIndexes();

		// write external metadata
		ArrayList<List<String>> metadata = new ArrayList<List<String>>();

		// filename, subject, path
		String fileName = "";
		field = response.getField("filename");
		if (field != null) {
			fileName = ((String) field.getValue()).toString();
			metadata.add(metadataResource.createTextMetadata(docIdHash, "filename", fileName));
		}
		field = response.getField("subject");
		if (field != null) {
			metadata.add(metadataResource.createTextMetadata(docIdHash, "subject", ((String) field.getValue()).toString()));
		} else {
			if (!fileName.isEmpty()) {
				metadata.add(metadataResource.createTextMetadata(docIdHash, "subject", fileName));
			}
		}
		field = response.getField("path");
		if (field != null)
			metadata.add(metadataResource.createTextMetadata(docIdHash, "path", ((String) field.getValue()).toString()));
		
		// link to hover
		metadata.add(metadataResource.createTextMetadata(docIdHash, "Link", clientUrl + docId));

		// attachments
		field = response.getField("attachments");
		if (field != null)
			metadata.add(metadataResource.createTextMetadata(docIdHash, "attachments", ((Boolean) field.getValue()).toString()));
		// content-type
		field = response.getField("content-type");
		if (field != null)
			metadata.add(metadataResource.createTextMetadata(docIdHash, "content-type", (String) field.getValue()));
		// file-type
		field = response.getField("filetype");
		if (field != null)
			metadata.add(metadataResource.createTextMetadata(docIdHash, "filetype", (String) field.getValue()));
		// from
		field = response.getField("from");
		if (field != null) {
			for (String email : extractEmail((String) field.getValue())) {
				metadata.add(metadataResource.createTextMetadata(docIdHash, "from", email));
			}
		}
		// to
		field = response.getField("to");
		if (field != null) {
			for (Object toList : field.getValues()) {
				for (String email : extractEmail((String) toList)) {
					metadata.add(metadataResource.createTextMetadata(docIdHash, "to", email));
				}
			}
		}
		metadataResource.appendMetadata(metadata);

	}

	private ArrayList<String> extractEmail(String s) {
		ArrayList<String> emails = new ArrayList<String>();
		Matcher m = emailPattern.matcher(s);
		while (m.find()) {
			emails.add(m.group());
		}
		return emails;
	}



	public Progress[] getProgress() {
		return new Progress[] {
				new ProgressImpl(
						Long.valueOf(currentRecord).intValue() - 1,
						Long.valueOf(totalRecords).intValue(),
						Progress.ENTITIES
						)
		};
	}

	public boolean hasNext() throws IOException, CollectionException {
		if (currentRecord < totalRecords) {
			currentRecord++;
			return true;
		} else {
			return false;
		}
	}

}

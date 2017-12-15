package com.mnsuk.converter;

import static com.ibm.dataexplorer.converter.LoggingConstants.PUBLIC_ENTRY;
import static com.ibm.dataexplorer.converter.LoggingConstants.PUBLIC_EXIT;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.ibm.dataexplorer.converter.ByteArrayConverter;
import com.ibm.dataexplorer.converter.ConversionException;
import com.ibm.dataexplorer.converter.ConverterOptions;
import com.ibm.dataexplorer.converter.FatalConverterException;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.NaturalLanguageUnderstanding;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.AnalysisResults;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.AnalyzeOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.ConceptsOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.DocumentEmotionResults;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.DocumentSentimentResults;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.EmotionOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.EmotionResult;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.EmotionScores;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.EntitiesOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.EntitiesResult;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.EntityMention;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.FeatureSentimentResults;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.Features;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.SentimentOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.SentimentResult;
import com.vivisimo.parser.input.ConverterInput;
import com.vivisimo.parser.input.InputFilter;
import com.vivisimo.parser.input.InputFilterFactory;
import com.vivisimo.parser.input.VXMLInputBuilder;

public class NLU implements ByteArrayConverter {
	private static final int MAX_NLU_TEXT = 50000;
	private static final Logger LOGGER = LoggerFactory.getLogger(NLU.class);
	private NLUConverterOptions opts;
	private boolean isAlive;
	private NaturalLanguageUnderstanding nlu;
	private Features features;
	
	public NLU(ConverterOptions options) throws FatalConverterException {
		LOGGER.trace("entry");
		this.opts = new NLUConverterOptions(options);
		try { 
			nlu = new NaturalLanguageUnderstanding(NaturalLanguageUnderstanding.VERSION_DATE_2017_02_27, opts.username, opts.password);
			EmotionOptions emotionOpts = new EmotionOptions.Builder().build();
			SentimentOptions sentimentOpts = new SentimentOptions.Builder()
					.document(true)
					.build();
			ConceptsOptions concepts = new ConceptsOptions.Builder()
					.limit(5)
					.build();
			EntitiesOptions entities;
			if (opts.defaultModel) {
				entities = new EntitiesOptions.Builder()
						.mentions(true)
						.sentiment(true)
						.emotion(true)
						.build();
			} else {
				opts.targetedEmotion = false; // override user settings, 
				opts.targetedSentiment = false; // these are invalid with custom models.
				entities = new EntitiesOptions.Builder()
						.mentions(true)
						.model(opts.model)
						.sentiment(false)
						.emotion(false)
						.build();
			}
			String buildOpts = "";
			buildOpts += opts.sentimentAnalysis ? "Y" : "N";
			buildOpts += opts.emotionAnalysis ? "Y" : "N";
			buildOpts += opts.entityExtraction ? "Y" : "N";
			switch (buildOpts) {
			case "NNY":
				features = new Features.Builder().entities(entities).build();
				break;
			case "NYN":
				features = new Features.Builder().emotion(emotionOpts).build();
				break;
			case "NYY":
				features = new Features.Builder().emotion(emotionOpts).entities(entities).build();
				break;
			case "YNN":
				features = new Features.Builder().sentiment(sentimentOpts).build();
				break;
			case "YNY":
				features = new Features.Builder().sentiment(sentimentOpts).entities(entities).build();
				break;
			case "YYN":
				features = new Features.Builder().emotion(emotionOpts).sentiment(sentimentOpts).build();
				break;
			default:
				features = new Features.Builder().emotion(emotionOpts).sentiment(sentimentOpts).entities(entities).build();	
			}
			isAlive = true;
		} catch (Exception e) {
			e.printStackTrace();
			LOGGER.info("Exception: "+ e.getMessage());
			throw new FatalConverterException("Error in nlutest converter init: " + e.getMessage(), (Throwable) e);
		}
		LOGGER.trace(this.getClass().getCanonicalName()+"#constructer exit");
	}

	@Override
	public byte[] convert(byte[] data) throws ConversionException, FatalConverterException {
		LOGGER.trace(PUBLIC_ENTRY);
		checkIsAlive();
		Map<String,Double> documentResults = new HashMap<String,Double>();
		List<NLUEntity> entityResults = new ArrayList<NLUEntity>();
		try {
			String stringData = convertToString(data);
			Throwable throwable = null;
			try {
				VXMLInputBuilder inputBuilder = new VXMLInputBuilder(stringData);
				for (ConverterInput inputDocument : inputBuilder.documents()) {
					InputFilter filter = InputFilterFactory.createInputFilter(inputDocument, opts.contentList);
					String filteredContents = filter.filterInput(opts.excludeByDefault);
					if (filteredContents == null || filteredContents.isEmpty()) {
						continue;
					}
					if (filteredContents.length() > MAX_NLU_TEXT) {
						filteredContents= filteredContents.substring(0, MAX_NLU_TEXT);
						LOGGER.info("Document text larger then NLU limit, truncated");
					}
						
					AnalyzeOptions standardParameters;
					standardParameters = new AnalyzeOptions.Builder()
							.text(filteredContents)
							.features(features)
							.returnAnalyzedText(false)
							.build();
					AnalysisResults standardResults = nlu.analyze(standardParameters).execute();
					if (opts.emotionAnalysis) {
						EmotionResult emotionResult = standardResults.getEmotion();
						if (emotionResult != null) {
							DocumentEmotionResults dem = emotionResult.getDocument();
							if (dem != null) {
								documentResults.put("anger", dem.getEmotion().getAnger());
								documentResults.put("disgust", dem.getEmotion().getDisgust());
								documentResults.put("fear", dem.getEmotion().getFear());
								documentResults.put("joy", dem.getEmotion().getJoy());
								documentResults.put("sadness", dem.getEmotion().getSadness());
							}
						}
					}
					if (opts.sentimentAnalysis) {
						SentimentResult sentimentResult = standardResults.getSentiment();
						if (sentimentResult != null) {
							DocumentSentimentResults dsr = sentimentResult.getDocument();
							if (dsr != null) {
								documentResults.put("sentiment", dsr.getScore());
							}
						}
					}
					appendDocScores(inputDocument.getDocumentElement(), documentResults);
					if (opts.entityExtraction) {
						List<EntitiesResult> er = standardResults.getEntities();
						for (EntitiesResult res : er) {
							Long count = res.getCount();
							Double relevance = res.getRelevance();
							FeatureSentimentResults fsr = null;
							EmotionScores es = null;
							/* temporary fix. Hard code count to 1 as it appears that
							 * the mention list size is always the same as the count.
							 * So if iterate on count and mentions, you get duplicated results
							 */
							count = 1L;
							for (int i=0; i<count; i++) {
								List<EntityMention> em = res.getMentions();
								if (em == null || em.isEmpty())
									continue;
								for (EntityMention mention : em) {
									List<Long> offsets = mention.getLocation();
									int start = offsets.get(0).intValue();
									int end = offsets.get(1).intValue();
									NLUEntity ent = new NLUEntity(mention.getText(),start, end);				
									ent.setType(res.getType());
									if (opts.defaultModel) {
										ent.setSource("default");
										ent.setRelevance(relevance);
									} else {
										ent.setSource(opts.model);
									}
									if (opts.targetedSentiment) {
										fsr = res.getSentiment();
										if (fsr != null) {
											ent.setSentiment(fsr.getScore().toString());
										}	
									}
									if (opts.targetedEmotion) {
										es = res.getEmotion();
										if ( es != null) {
											ent.setAnger(es.getAnger().toString());
											ent.setDisgust(es.getDisgust().toString());
											ent.setFear(es.getFear().toString());
											ent.setJoy(es.getJoy().toString());
											ent.setSadness(es.getSadness().toString());
										}
									}
									entityResults.add(ent);
								}
							}
						}
						appendEntities(inputDocument.getDocumentElement(), entityResults);
					}
				} 
				return convertToBytes(inputBuilder.newOutputBuilder(opts.excludeByDefault).toString());
			} catch (Throwable inputBuilder) {
				throwable = inputBuilder;
				throw inputBuilder;
			}
		} catch (Exception e) {
			throw new ConversionException("Error NLU converter: " + e.getMessage(), (Throwable) e);
		} 
		finally {
			LOGGER.trace(PUBLIC_EXIT);
		}
	}

	private void appendDocScores(Element stringData, Map<String, Double> documentResults) throws SAXException, IOException, ParserConfigurationException, TransformerException{
		Iterator it = documentResults.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry)it.next();
			System.out.println(pair.getKey() + " = " + pair.getValue());
			Element newContent = stringData.getOwnerDocument().createElement("content");
			newContent.setAttribute("name", "nlu_document_" + (String) pair.getKey());
			newContent.setAttribute("type", "text");
			String val = ((Double) pair.getValue()).toString();
			newContent.appendChild(stringData.getOwnerDocument().createTextNode(val));
			stringData.appendChild(newContent);
			it.remove(); // avoids a ConcurrentModificationException
		}
	}

	private void appendEntities(Element stringData, List<NLUEntity> entities) throws SAXException, IOException, ParserConfigurationException, TransformerException{
		for (NLUEntity ent : entities) {
			Element newContent = stringData.getOwnerDocument().createElement("content");
			newContent.setAttribute("name", "nlu_entity_" + ent.getType());
			newContent.setAttribute("source", ent.getSource());
			newContent.setAttribute("type", "text");
			if (opts.targetedEmotion) {
				newContent.setAttribute("anger", ent.getAnger());
				newContent.setAttribute("disgust", ent.getDisgust());
				newContent.setAttribute("fear", ent.getFear());
				newContent.setAttribute("joy", ent.getJoy());
				newContent.setAttribute("sadness", ent.getSadness());
			}
			if (opts.targetedSentiment) 
				newContent.setAttribute("sentiment", ent.getSentiment());
			if (opts.defaultModel) 
				newContent.setAttribute("relevance", ent.getRelevance());
			if (opts.entityOffsets) {
				newContent.setAttribute("begin", ent.getBegin());
				newContent.setAttribute("end", ent.getEnd());
			}
			String val = ent.getCoveredText();
			newContent.appendChild(stringData.getOwnerDocument().createTextNode(val));
			stringData.appendChild(newContent);
		}
	}

	private byte[] convertToBytes(String data) throws UnsupportedEncodingException {
		return data == null ? null : data.getBytes("UTF-8");
	}

	private String convertToString(byte[] data) throws UnsupportedEncodingException {
		return data == null ? "" : new String(data, "UTF-8");
	}

	private void checkIsAlive() {
		if (!isAlive()) {
			LOGGER.error("I've already been terminated");
			throw new IllegalStateException("The object has already been terminated");
		}
	}

	boolean isAlive() {
		return isAlive;
	}

	@Override
	public void terminate() {
		checkIsAlive();
		LOGGER.debug("Terminating");
		isAlive = false;
	}

	public class NLUEntity {
		String ct, source, type, relevance;
		String begin, end;
		String anger, disgust, fear, joy, sadness, sentiment;
		NLUEntity(String coveredText, int begin, int end) {
			this.ct=coveredText;
			this.begin=Integer.toString(begin);
			this.end=Integer.toString(end);
		}
		public String getCoveredText() {
			return ct;
		}
		public String getBegin() {
			return begin;
		}
		public String getEnd() {
			return end;
		}
		public String getRelevance() {
			return relevance;
		}
		public void setRelevance(Double rel) {
			this.relevance = Double.toString(rel);
		}
		public String getAnger() {
			return anger;
		}
		public void setAnger(String anger) {
			this.anger = anger;
		}
		public String getDisgust() {
			return disgust;
		}
		public void setDisgust(String disgust) {
			this.disgust = disgust;
		}
		public String getFear() {
			return fear;
		}
		public void setFear(String fear) {
			this.fear = fear;
		}
		public String getJoy() {
			return joy;
		}
		public void setJoy(String joy) {
			this.joy = joy;
		}
		public String getSadness() {
			return sadness;
		}
		public void setSadness(String sadness) {
			this.sadness = sadness;
		}
		public String getSentiment() {
			return sentiment;
		}
		public void setSentiment(String sentiment) {
			this.sentiment = sentiment;
		}
		public String getSource() {
			return source;
		}
		public void setSource(String source) {
			this.source = source;
		}
		public String getType() {
			return type;
		}
		public void setType(String type) {
			this.type = type;
		}
	}

}

package com.mnsuk.converter;

import java.util.HashSet;
import java.util.List;

import com.ibm.dataexplorer.converter.ConverterOptions;
import com.ibm.dataexplorer.converter.FatalConverterException;

public class NLUConverterOptions {
	private static final String OPTION_USERNAME = "username";
	private static final String OPTION_PASSWORD = "password";
	private static final String OPTION_SENTIMENT_ANALYSIS = "enable-sentiment-analysis";
	private static final String OPTION_EMOTION_ANALYSIS = "enable-emotion-analysis";
	private static final String OPTION_ENTITY_EXTRACTION = "enable-entity-extraction";
	private static final String OPTION_MODEL = "model";
	private static final String OPTION_TARGETED_EMOTION = "enable-targeted-emotion";
	private static final String OPTION_TARGETED_SENTIMENT = "enable-targeted-sentiment";
	private static final String OPTION_ENTITY_OFFSETS = "enable-entity-offsets";
	private static final String OPTION_EXCLUDE_BY_DEFAULT = "exclude-by-default";
	private static final String OPTION_CONTENT_LIST = "content-list";
	
	public String username;
	public String password;
	public String model;
	public boolean defaultModel;
	public boolean sentimentAnalysis;
	public boolean emotionAnalysis;
	public boolean entityExtraction;
	public boolean targetedEmotion;
	public boolean targetedSentiment;
	public boolean entityOffsets;
	public boolean excludeByDefault;
	public HashSet<String> contentList = new HashSet<String>();
	
	public NLUConverterOptions(ConverterOptions options) {
		this.username = options.getLastOptionValue(OPTION_USERNAME);
		this.password = options.getLastOptionValue(OPTION_PASSWORD);
		this.model = options.getLastOptionValue(OPTION_MODEL);
		this.defaultModel = this.model.equalsIgnoreCase("default") ? true : false;
		this.sentimentAnalysis = OPTION_SENTIMENT_ANALYSIS.equals(options.getLastOptionValue(OPTION_SENTIMENT_ANALYSIS));
		this.emotionAnalysis = OPTION_EMOTION_ANALYSIS.equals(options.getLastOptionValue(OPTION_EMOTION_ANALYSIS));
		this.entityExtraction = OPTION_ENTITY_EXTRACTION.equals(options.getLastOptionValue(OPTION_ENTITY_EXTRACTION));
		this.targetedEmotion = OPTION_TARGETED_EMOTION.equals(options.getLastOptionValue(OPTION_TARGETED_EMOTION));
		this.targetedSentiment = OPTION_TARGETED_SENTIMENT.equals(options.getLastOptionValue(OPTION_TARGETED_SENTIMENT));
		this.entityOffsets = OPTION_ENTITY_OFFSETS.equals(options.getLastOptionValue(OPTION_ENTITY_OFFSETS));
		this.excludeByDefault = OPTION_EXCLUDE_BY_DEFAULT.equals(options.getLastOptionValue(OPTION_EXCLUDE_BY_DEFAULT));
		this.contentList.addAll(options.getOptionValues(OPTION_CONTENT_LIST));
	}
	
	public void validateOptions() {
		Boolean stub = false;
		if (stub) { //stub
			throw new FatalConverterException("Required option missing");
		}
	}

}

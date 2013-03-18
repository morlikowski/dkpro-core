/*******************************************************************************
 * Copyright 2012
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl-3.0.txt
 ******************************************************************************/
package de.tudarmstadt.ukp.dkpro.core.matetools;

import is2.tag3.Tagger;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.util.JCasUtil;

import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.resources.CasConfigurableProviderBase;
import de.tudarmstadt.ukp.dkpro.core.api.resources.MappingProvider;
import de.tudarmstadt.ukp.dkpro.core.api.resources.ResourceUtils;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;

/**
 * <p>
 * DKPro Annotator for the MateToolsPosTagger
 * </p>
 * 
 * Required annotations:<br/>
 * <ul>
 * <li>Sentence</li>
 * <li>Token</li>
 * <li>Lemma</li>
 * </ul>
 * 
 * Generated annotations:<br/>
 * <ul>
 * <li>POSTag</li>
 * </ul>
 * 
 * 
 * @author AnNa, zesch
 */
public class MatePosTagger
	extends JCasAnnotator_ImplBase
{
	/**
	 * Use this language instead of the language set in the CAS to locate the model.
	 */
	public static final String PARAM_LANGUAGE = ComponentParameters.PARAM_LANGUAGE;
	@ConfigurationParameter(name = PARAM_LANGUAGE, mandatory = false)
	protected String language;

	/**
	 * Override the default variant used to locate the model.
	 */
	public static final String PARAM_VARIANT = ComponentParameters.PARAM_VARIANT;
	@ConfigurationParameter(name = PARAM_VARIANT, mandatory = false)
	protected String variant;

	/**
	 * Load the model from this location instead of locating the model automatically.
	 */
	public static final String PARAM_MODEL_LOCATION = ComponentParameters.PARAM_MODEL_LOCATION;
	@ConfigurationParameter(name = PARAM_MODEL_LOCATION, mandatory = false)
	protected String modelLocation;

	/**
	 * Load the part-of-speech tag to UIMA type mapping from this location instead of locating the
	 * mapping automatically.
	 */
	public static final String PARAM_POS_MAPPING_LOCATION = ComponentParameters.PARAM_POS_MAPPING_LOCATION;
	@ConfigurationParameter(name = PARAM_POS_MAPPING_LOCATION, mandatory = false)
	protected String posMappingLocation;

	private CasConfigurableProviderBase<Tagger> modelProvider;
	private MappingProvider posMappingProvider;

	@Override
	public void initialize(UimaContext aContext)
		throws ResourceInitializationException
	{
		super.initialize(aContext);

		modelProvider = new CasConfigurableProviderBase<Tagger>()
		{
			{
				setDefault(VERSION, "20120626.0");
				setDefault(GROUP_ID, "de.tudarmstadt.ukp.dkpro.core");
				setDefault(ARTIFACT_ID,
						"de.tudarmstadt.ukp.dkpro.core.matetools-model-tagger-${language}-${variant}");

				setDefault(LOCATION, "classpath:/de/tudarmstadt/ukp/dkpro/core/matetools/lib/"
						+ "tagger-${language}-${variant}.model");
				setDefault(VARIANT, "crosstrain");

				setOverride(LOCATION, modelLocation);
				setOverride(LANGUAGE, language);
				setOverride(VARIANT, variant);
			}

			@Override
			protected Tagger produceResource(URL aUrl)
				throws IOException
			{
				java.io.File modelFile = ResourceUtils.getUrlAsFile(aUrl, true);

				String[] args = { "-model", modelFile.getPath() };
				is2.tag3.Options option = new is2.tag3.Options(args);
				return new is2.tag3.Tagger(option); // create a POSTagger
			}
		};

		posMappingProvider = new MappingProvider();
		posMappingProvider.setDefault(MappingProvider.LOCATION, "classpath:/de/tudarmstadt/ukp/dkpro/"
				+ "core/api/lexmorph/tagset/${language}-${tagger.tagset}-tagger.map");
		posMappingProvider.setDefault(MappingProvider.BASE_TYPE, POS.class.getName());
		posMappingProvider.setDefault("tagger.tagset", "default");
		posMappingProvider.setOverride(MappingProvider.LOCATION, posMappingLocation);
		posMappingProvider.setOverride(MappingProvider.LANGUAGE, language);
		posMappingProvider.addImport("tagger.tagset", modelProvider);
	}

	@Override
	public void process(JCas jcas)
		throws AnalysisEngineProcessException
	{
		CAS cas = jcas.getCas();

		modelProvider.configure(cas);
		posMappingProvider.configure(cas);

		for (Sentence sentence : JCasUtil.select(jcas, Sentence.class)) {
			List<Token> tokens = JCasUtil.selectCovered(Token.class, sentence);
			List<Lemma> lemmas = JCasUtil.selectCovered(Lemma.class, sentence);

			String[] posTags = modelProvider.getResource().tag(
					JCasUtil.toText(tokens).toArray(new String[0]),
					JCasUtil.toText(lemmas).toArray(new String[0]));

			for (int i = 0; i < posTags.length; i++) {
				Token token = tokens.get(i);
				Type posType = posMappingProvider.getTagType(posTags[i]);
				POS posTag = (POS) cas.createAnnotation(posType, token.getBegin(), token.getEnd());
				posTag.setPosValue(posTags[i]);
				posTag.addToIndexes();
				token.setPos(posTag);
			}
		}
	}
}

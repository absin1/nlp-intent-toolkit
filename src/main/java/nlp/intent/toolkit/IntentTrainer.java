package nlp.intent.toolkit;

import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.*;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.lang.ArrayUtils;

public class IntentTrainer {

	public static void main(String[] args) throws Exception {

		File trainingDirectory = new File("example/smallTalk/train");
		String[] slots = new String[0];
		if (args.length > 1) {
			slots = "city".split(",");
		}

		if (!trainingDirectory.isDirectory()) {
			throw new IllegalArgumentException(
					"TrainingDirectory is not a directory: " + trainingDirectory.getAbsolutePath());
		}

		List<ObjectStream<DocumentSample>> categoryStreams = new ArrayList<ObjectStream<DocumentSample>>();
		for (File trainingFile : trainingDirectory.listFiles()) {
			String intent = trainingFile.getName().replaceFirst("[.][^.]+$", "");
			ObjectStream<String> lineStream = new PlainTextByLineStream(new FileInputStream(trainingFile), "UTF-8");
			ObjectStream<DocumentSample> documentSampleStream = new IntentDocumentSampleStream(intent, lineStream);
			categoryStreams.add(documentSampleStream);
		}
		ObjectStream<DocumentSample> combinedDocumentSampleStream = ObjectStreamUtils
				.createObjectStream(categoryStreams.toArray(new ObjectStream[0]));

		DoccatModel doccatModel = DocumentCategorizerME.train("en", combinedDocumentSampleStream, 0, 100);
		combinedDocumentSampleStream.close();

		List<TokenNameFinderModel> tokenNameFinderModels = new ArrayList<TokenNameFinderModel>();

		for (String slot : slots) {
			List<ObjectStream<NameSample>> nameStreams = new ArrayList<ObjectStream<NameSample>>();
			for (File trainingFile : trainingDirectory.listFiles()) {
				ObjectStream<String> lineStream = new PlainTextByLineStream(new FileInputStream(trainingFile), "UTF-8");
				ObjectStream<NameSample> nameSampleStream = new NameSampleDataStream(lineStream);
				nameStreams.add(nameSampleStream);
			}
			ObjectStream<NameSample> combinedNameSampleStream = ObjectStreamUtils
					.createObjectStream(nameStreams.toArray(new ObjectStream[0]));

			TokenNameFinderModel tokenNameFinderModel = NameFinderME.train("en", "city", combinedNameSampleStream,
					TrainingParameters.defaultParams(), (AdaptiveFeatureGenerator) null,
					Collections.<String, Object>emptyMap());
			combinedNameSampleStream.close();
			tokenNameFinderModels.add(tokenNameFinderModel);
		}

		DocumentCategorizerME categorizer = new DocumentCategorizerME(doccatModel);
		NameFinderME[] nameFinderMEs = new NameFinderME[tokenNameFinderModels.size()];
		for (int i = 0; i < tokenNameFinderModels.size(); i++) {
			nameFinderMEs[i] = new NameFinderME(tokenNameFinderModels.get(i));
		}

		System.out.println("Training complete. Ready.");
		System.out.print(">");
		String s;
		Scanner scanner = new Scanner(System.in);
		while ((s = scanner.nextLine()) != null) {
			double[] outcome = categorizer.categorize(s);
			if (Collections.max(Arrays.asList(ArrayUtils.toObject(outcome))) < 0.2)
				System.out.print("action=" + "fallback" + " args={ ");
			else {
				System.out.print("action=" + categorizer.getBestCategory(outcome) + " args={ ");
				String[] tokens = WhitespaceTokenizer.INSTANCE.tokenize(s);
				for (NameFinderME nameFinderME : nameFinderMEs) {
					Span[] spans = nameFinderME.find(tokens);
					String[] names = Span.spansToStrings(spans, tokens);
					for (int i = 0; i < spans.length; i++) {
						System.out.print(spans[i].getType() + "=" + names[i] + " ");
					}
				}
			}
			System.out.println("}");
			System.out.print(">");

		}
		scanner.close();
	}

}

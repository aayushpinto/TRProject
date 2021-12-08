import edu.stanford.nlp.simple.Sentence;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import edu.stanford.nlp.util.StringUtils;
import org.tartarus.snowball.ext.PorterStemmer;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class IndexTrainer {

    final static String WIKI_PAGES_PATH = "wikipages";
    final static String INDEX_PATH = "src/main/resources/index";


    public static void buildIndex() {
        System.out.println("Building Index ...");

        File file1 = new File(StringUtils.join(new String[]{System.getProperty("user.dir"), INDEX_PATH}, "/"));
        StandardAnalyzer standardAnalyzer = new StandardAnalyzer();
        Directory directory = null;
        try {
            System.out.println(file1.toPath());
            directory = FSDirectory.open(file1.toPath());
            IndexWriterConfig indexWriterConfig = new IndexWriterConfig(standardAnalyzer);
            IndexWriter indexWriter = new IndexWriter(directory, indexWriterConfig);
            File[] files;
            files = new File(IndexTrainer.class.getClassLoader().getResource(WIKI_PAGES_PATH).toURI()).listFiles();
            if (files != null) {
                int processed = 0;
                for (File file : files) {
                    System.out.println("processing... " + processed);
                    processFile(file, indexWriter);
                    processed++;
                }
            }
            indexWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void processFile(File file, IndexWriter w) {
        System.out.println("Processing file" + file);
        try (Scanner inputScanner = new Scanner(file)) {

            String title = "";
            String categories = "";
            StringBuilder subHeadings = new StringBuilder();
            StringBuilder result = new StringBuilder();

            Matcher titleMatcher;
            Matcher subHeadingMatcher;

            while (inputScanner.hasNextLine()) {
                String line = inputScanner.nextLine();
                if (line.equals('\n')) {
                    continue;
                } else if ((titleMatcher = Pattern.compile("\\[\\[.*\\]\\]\\n?").matcher(line)).matches()) {

                    if (!title.equals("")) {
                        addDoc(w, title, categories, subHeadings.toString(), result.toString());
                    }
                    title = RemoveSpecialCharacters(titleMatcher.toMatchResult().group().replace('[', ' ').replace(']', ' ').trim());
                    result = new StringBuilder();
                    categories = "";
                    subHeadings = new StringBuilder();

                } else if ((subHeadingMatcher = Pattern.compile("\\=\\=.*\\=\\=\\n?").matcher(line)).matches()) {
                    subHeadings.append(RemoveSpecialCharacters(subHeadingMatcher.toMatchResult().group().replace('=', ' ').trim() + " ")).append(" ");
                } else if (line.indexOf("CATEGORIES:") == 0) {
                    categories = RemoveSpecialCharacters(line.substring(12));
                } else {
                    result.append(RemoveSpecialCharacters(line));
                    result.append(" ");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void addDoc(IndexWriter w, String title, String categories, String subHeadings, String result) throws IOException {
        Document document = new Document();
        result = categories + " " + subHeadings + " " + result;
        document.add(new StringField("docid", title, Field.Store.YES));
        document.add(new TextField("categories", categories, Field.Store.YES));
        document.add(new TextField("subheadings", subHeadings, Field.Store.YES));
        document.add(new TextField("content", result, Field.Store.YES));
        w.addDocument(document);
    }

    private static String RemoveSpecialCharacters(String str) {
        return str.replaceAll("\n", " ")
                .replaceAll("\\[\\s*tpl\\s*\\]", " ")
                .replaceAll("\\[\\s*/\\s*tpl\\s*\\]", " ")
                .replaceAll("https?://\\S+\\s?", "")
                .replaceAll("[^ a-zA-Z\\d]", " ")
                .toLowerCase().trim();
    }

    public static String lemmatization(String str) {
        StringBuilder result = new StringBuilder();
        if (str.isEmpty()) {
            return str;
        }
        for (String lemma : new Sentence(str.toLowerCase()).lemmas()) {
            result.append(lemma).append(" ");
        }
        return result.toString();
    }

    public static String stemming(String str) {
        StringBuilder result = new StringBuilder();
        if (str.isEmpty()) {
            return str;
        }
        for (String word : new Sentence(str.toLowerCase()).words()) {
            result.append(getStem(word)).append(" ");
        }
        return result.toString();
    }

    public static String getStem(String term) {
        PorterStemmer stemmer = new PorterStemmer();
        stemmer.setCurrent(term);
        stemmer.stem();
        return stemmer.getCurrent();
    }

    public static void main(String[] args) throws URISyntaxException {
        IndexTrainer.buildIndex();
    }
}
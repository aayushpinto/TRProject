import edu.stanford.nlp.util.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import java.io.*;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.tartarus.snowball.ext.PorterStemmer;

import edu.stanford.nlp.simple.Sentence;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.*;


class Question {
    private String category = "";
    private String question = "";
    private String answer = "";

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    @Override
    public String toString() {
        return "Question{" +
                "category='" + category + '\'' +
                ", question='" + question + '\'' +
                ", answer='" + answer + '\'' +
                '}';
    }
}

public class QueryEngine {
    final static String indexPath = "src/main/resources/index";
    final static String questionsFilePath = "questions.txt";
    static IndexSearcher searcher;
    static Analyzer analyzer;
    static IndexReader indexReader;
    static List<Question> questions = new ArrayList<>();

    static {
        try {
            String indexPath = StringUtils.join(new String[]{System.getProperty("user.dir"), IndexTrainer.INDEX_PATH}, "/");
            Directory indexDirectory = FSDirectory.open(Paths.get(indexPath));
            indexReader = DirectoryReader.open(indexDirectory);
            searcher = new IndexSearcher(indexReader);
            analyzer = new StandardAnalyzer();

            File file = new File(QueryEngine.class.getClassLoader().getResource(questionsFilePath).toURI());
            try (Scanner inputScanner = new Scanner(file)) {
                int cnt = 2;
                Question question = new Question();
                while (inputScanner.hasNextLine()) {
                    String line = inputScanner.nextLine();
                    if (line.equals('\n') || cnt == -1) {
                        cnt = 2;
                        continue;
                    }
                    switch (cnt) {
                        case 2:
                            question.setCategory(line.trim());
                            break;
                        case 1:
                            question.setQuestion(line.trim());
                            break;
                        case 0:
                            question.setAnswer(line.trim());
                            questions.add(question);
                            question = new Question();
                            break;
                    }
                    cnt--;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

//            System.out.println(questions);

        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) throws IOException, ParseException {

//        BM25
        QueryEngine.Pat1_Performace();

//
        searcher.setSimilarity(new LMDirichletSimilarity());
        QueryEngine.Pat1_Performace();



//        LMD
        searcher.setSimilarity(new LMJelinekMercerSimilarity(0.2F));
        QueryEngine.Pat1_Performace();


        QueryEngine.reRankingWithlanguageModel();


    }

    public static void Pat1_Performace() throws IOException, ParseException {
        int cnt = 0;
        int hitPerPage = 10;
        for (Question item : questions) {
            Query q = new QueryParser("content", analyzer).parse(QueryParser.escape(item.getQuestion()));
            TopDocs docs = searcher.search(q, 1);
            ScoreDoc[] hitsDocs = docs.scoreDocs;
            if (hitsDocs.length > 0) {
                int docId = hitsDocs[0].doc;
                Document docName = searcher.doc(docId);
                if (item.getAnswer().toLowerCase().contains(docName.get("docid").trim())) {
                    cnt++;
                }
            }

        }
        System.out.println("-----------");
        System.out.println("%% P@1: " + cnt + " / " + questions.size());

    }


    public static List<ResultClass> runQuery(String query) {
        List<ResultClass> ans = new ArrayList<ResultClass>();
        query = stemming(lemmatization(RemoveSpecialCharacters(query)));
        try {
            Query q = new QueryParser("content", analyzer).parse(query);
            int hitsPerPage = 10;
            TopDocs docs = searcher.search(q, hitsPerPage);
            ScoreDoc[] hits = docs.scoreDocs;

            for (int i = 0; i < hits.length; i++) {
                ResultClass r = new ResultClass();
                int docId = hits[i].doc;
                r.DocName = searcher.doc(docId);
                r.docScore = hits[i].score;
                ans.add(r);
            }
        } catch (ParseException | IOException e) {
            e.printStackTrace();
        }
        return ans;
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

    public static void reRankingWithlanguageModel() throws IOException, ParseException {
        int cnt = 0;
        int hitPerPage = 10;
        int total = 0;
        int mmr = 0;
        for (Question item : questions) {
            List<ResultClass> rc = null;

            rc = queryWithLanguageModel(item.getCategory() + " " + item.getQuestion(), hitPerPage);
//            if (item.getAnswer().toLowerCase().contains(rc.get(0).DocName.get("docid").trim())) {
//                cnt++;
//            }
            if (item.getAnswer().toLowerCase().contains(rc.get(0).DocName.get("docid"))) {
                mmr += 1.0;
            } else {
                for (int j = 0; j < rc.size(); j++) {
                    if (item.getAnswer().toLowerCase().contains(rc.get(j).DocName.get("docid"))) {
                        mmr += (double) 1 / (j + 1);
                        break;
                    }
                }
            }
            total++;
        }
        double result = (double) mmr / total;
        System.out.println(result);
//        System.out.println("P@1: " + cnt + " / " + total);
    }

    public static List<ResultClass> queryWithLanguageModel(String queryExpr, int hitPerPage) throws ParseException, IOException {

        Query query = new QueryParser("content", analyzer).parse(QueryParser.escape(queryExpr));
        TopDocs topDocs = searcher.search(query, hitPerPage);

        return reRanking(queryExpr, topDocs);
    }

    private static List<ResultClass> reRanking(String queryExpr, TopDocs topDocs) throws IOException {
        List<ResultClass> res = new ArrayList<>();
        String[] queryArr = queryExpr.split(" ");

        Map<Integer, List<String>> docMap = new HashMap<>();

        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            List<String> content = Arrays.asList(searcher.doc(scoreDoc.doc).get("content").split(" "));

            docMap.put(scoreDoc.doc, content);
        }

        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            int docID = scoreDoc.doc;
            Document doc = searcher.doc(docID);
            List<String> tokens = docMap.get(scoreDoc.doc);

            double score = 1;
            for (String c : queryArr) {
                int num = tokens.size();
                int tf = Collections.frequency(tokens, c);
                double total = 0;
                double occurances = 0; //collection frequency
                for (int docid : docMap.keySet()) {
//                    System.out.println(docMap.get(docid));
                    occurances += Collections.frequency(docMap.get(docid), c);
                    total += docMap.get(docid).size();
                }
                double PtMc = occurances / total;
                double param = (tf + 0.5 * PtMc) / (num + 0.5);
                score *= param;
            }
            res.add(new ResultClass(doc, score));
        }
        Collections.sort(res, new Comparator<ResultClass>() {
            @Override
            public int compare(ResultClass o1, ResultClass o2) {
                int i = new Double(o2.docScore).compareTo(o1.docScore);
                return i;
            }
        });
        return res;
    }

}
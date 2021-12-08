import org.apache.lucene.document.Document;

public class ResultClass {
    Document DocName;
    double docScore = 0;

    public ResultClass() {

    }

    public ResultClass(Document doc, double docScore) {
        this.DocName = doc;
        this.docScore = docScore;
    }

}
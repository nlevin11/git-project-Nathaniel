public class CommitTester {
    public static void main(String[] args) {
        stage("./newFile.txt");
        commit("matthew", "this is a test");
        stage("./anotherFile.txt");
        commit("mateo", "this is a second commit");
        resetTestFiles("git");
    }
}

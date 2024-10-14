import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Blob {
    //Global variable to toggle compression on or off
    private static boolean compressionEnabled = false;
    
    //backs up a directory via making its tree given its path and returns the tree file
    public static String createTree (String directoryPath) throws IOException, NoSuchAlgorithmException, ObjectsDirectoryNotFoundException{
        File direct = new File (directoryPath);

        //checks if the directory exists
        if (!direct.exists()){
            throw new FileNotFoundException("Directory path does not exist.");
        }

        // Check if it's a directory
        if (!direct.isDirectory()) {
            throw new IOException("Path is not a directory: " + directoryPath);
        }

        try{
            //creates list of subfiles/folders and String for the tree of the directory
            File [] directoryList = direct.listFiles();
            StringBuilder tree = new StringBuilder();

            //goes through the directory
            for (int i=0; i<directoryList.length; i++){
                //creates the current path for naming reasons
                String pathAt = "";
                if (directoryList[i].getParent()==null){
                    pathAt = directoryList[i].getName();
                }
                else{
                    pathAt = directoryList[i].getParentFile().getName() + "/" + directoryList[i].getName();
                }

                if (directoryList[i].isDirectory()){
                    //recursively goes through this directory (depth first search)
                    createTree (directoryList[i].getPath());
                    //new entry into the tree string
                    

                    //existing bug ************************************************************DISREGARD******************************************************************
                    tree.append("tree " + generateSha1(createTree(directoryList[i].getPath())) + " " + pathAt + "\n");
                }
                else{
                    String fileContent = new String (Files.readAllBytes(Paths.get(directoryList[i].getPath())));
                
                    //new entry into the tree file
                    tree.append("blob "+ generateSha1(fileContent) + " " + pathAt + "\n");

                    //creates a new blob of the file
                    createBlob(directoryList[i].getPath());
                }
            }

            //creates a blob for the tree string by basically rewriting 
            String shaOfTree = generateSha1(tree.toString());
            //Checks if the objects directory exists
            if (!Files.exists(Paths.get("git/objects"))) {
                throw new ObjectsDirectoryNotFoundException("Objects directory does not exist. Please initialize the repository");
            }

            //Reads the tree content and writes it into the blob
            Files.write(Paths.get("git/objects", shaOfTree), tree.toString().getBytes());
            
            //Writes a new line into the index
            String indexEntry = "tree " + shaOfTree + " " + directoryPath + "\n";
            
            BufferedWriter bw = new BufferedWriter(new FileWriter("git/index", true));
            bw.append (indexEntry);
            bw.close();
            return tree.toString();

        }
        catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    //Generates a unique filename using SHA1 hash of file data
    public static String generateSha1(String data) throws IOException, NoSuchAlgorithmException {
        //Creates SHA1 hash from the file content
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] hashBytes = sha1.digest(data.getBytes());
        //Converts hash bytes to string
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    //Reads file content and compresses it if enabled
    public static byte[] readFileContent(String filePath) throws IOException {
        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
        if (compressionEnabled) {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream(); //output stream that writes the input data into a byte array
            try (ZipOutputStream zipper = new ZipOutputStream(byteStream)){
                zipper.putNextEntry(new ZipEntry(Paths.get(filePath).getFileName().toString()));//Creates a new zip file to be written
                zipper.write(fileBytes); //Writes the original file bytes to the zip entry
                zipper.closeEntry(); 
            }
            catch (IOException e){
                e.printStackTrace();
            }
            return byteStream.toByteArray(); //Returns the compressed bytes
        }
        return fileBytes; //This only happens if compression is disabled
    }

    //Creates a new blob in the objects directory
    public static void createBlob(String filePath) throws IOException, NoSuchAlgorithmException, ObjectsDirectoryNotFoundException {
        String fileContent = new String (Files.readAllBytes(Paths.get(filePath)));
        String sha1Filename = generateSha1(fileContent);
        //Checks if the objects directory exists
        if (!Files.exists(Paths.get("git/objects"))) {
            throw new ObjectsDirectoryNotFoundException("Objects directory does not exist. Please initialize the repository");
        }

        //Reads the file content and writes it into the blob
        byte[] fileBytes = readFileContent(filePath);
        Files.write(Paths.get("git/objects", sha1Filename), fileBytes);
        
        //Writes a new line into the index
        // Determine whether it's a file or a directory
        String fileName = Paths.get(filePath).getFileName().toString();
        File fileAt = new File (fileName);
        String indexEntry = "";
        if (fileAt.isDirectory()){
            indexEntry = "tree " + sha1Filename + " " + filePath + "\n";
        }
        else{
            indexEntry = "blob " + sha1Filename + " " + filePath + "\n";
        }
        BufferedWriter bw = new BufferedWriter(new FileWriter("git/index", true));
        bw.append (indexEntry);
        bw.close();
    }

    //Tests the Blob creation, works with both compression and not
    public static void main(String[] args) throws NoSuchAlgorithmException, IOException, ObjectsDirectoryNotFoundException {
        resetTestFiles();
        try {
            Git.initGitRepo();
            resetTestFiles();
            //Creates string with 10,000 a's
            StringBuilder sb = new StringBuilder("");
            for (int i=0; i<10000; i++) {
                sb.append("a");
            }
            System.out.println("Compression Enabled: " + compressionEnabled);
            Files.write(Paths.get("example.txt"), sb.toString().getBytes(StandardCharsets.UTF_8));
            createBlob("example.txt");
            System.out.println("Blob created successfully");

            //Tests if the hash and file contents are correct
            String officialSha1Hash = generateSha1(sb.toString());
            boolean hashCreatedSuccessfully = Files.exists(Paths.get("git/objects", officialSha1Hash));
            boolean fileContentsCorrect = false;
            boolean indexContentCorrect = false;
            //Tests if the contents of the blob were copied correctly
            if (hashCreatedSuccessfully) {
                byte[] blobContentBytes = Files.readAllBytes(Paths.get("git/objects", officialSha1Hash));
                byte[] originalFileBytes = readFileContent("example.txt");
                fileContentsCorrect = java.util.Arrays.equals(blobContentBytes, originalFileBytes);
            }
            //Tests if the line in the index file is correctly formatted
            for (String line : Files.readAllLines(Paths.get("git/index"))) {
                if (line.equals(officialSha1Hash+" example.txt")) {
                    indexContentCorrect=true;
                    break;
                }
            }
            //Tests if compression works
            if (compressionEnabled) {
                byte[] blobContentBytes = Files.readAllBytes(Paths.get("git/objects", officialSha1Hash));
                byte[] uncompressedBytes = Files.readAllBytes(Paths.get("example.txt"));
                System.out.println("Original Size: " + uncompressedBytes.length);
                System.out.println("Compressed Size: " + blobContentBytes.length);
            }

            System.out.println("Hash created successfully: " + hashCreatedSuccessfully);
            System.out.println("File contents correct: " + fileContentsCorrect);
            System.out.println("Index line Correct: " + indexContentCorrect);
        } catch (IOException | NoSuchAlgorithmException | ObjectsDirectoryNotFoundException e) {
            e.printStackTrace();
        }

        //creates directory with subfiles and folders
        File newDirectory = new File ("direct");
        if (!newDirectory.exists()){
            newDirectory.mkdir();
        }
        File newFile = new File ("direct/newFile.txt");
        File emptDir = new File ("direct/directEmpt");
        StringBuilder sb2 = new StringBuilder("aoeunvoacwn");
        Files.write(Paths.get("direct/newFile.txt"), sb2.toString().getBytes(StandardCharsets.UTF_8));
        if (!newFile.exists()){
            newFile.createNewFile();
        }
        if (!emptDir.exists()){
            emptDir.mkdir();
        }
        File newDirectory2 = new File ("direct/direct2");
        if (!newDirectory2.exists()){
            newDirectory2.mkdir();
        }
        File newFile2 = new File ("direct/direct2/newFile2.txt");
        StringBuilder sb3 = new StringBuilder("aoeunvoacwnv");
        Files.write(Paths.get("direct/direct2/newFile2.txt"), sb3.toString().getBytes(StandardCharsets.UTF_8));
        if (!newFile2.exists()){
            newFile2.createNewFile();
        }

        //creates a tree from the top directory
        createTree("./direct");

        //checks the index entry
        BufferedReader br = Files.newBufferedReader(Paths.get("git/index"));
        br.readLine();
        if (br.readLine().equals("blob " + generateSha1("aoeunvoacwn") + " direct/newFile.txt")){
            if (br.readLine().equals("blob " + generateSha1("aoeunvoacwnv") + " direct/direct2/newFile2.txt")){
                if (br.readLine().equals("tree 76617d2c07b3662a10b6815823eb87c40d06bc16 direct/direct2")){
                    if (br.readLine().equals("tree 91db97839ff6b11ddb0498577aa074b24315a9bf direct")){
                        System.out.println ("index is correctly updated with tree.");
                    }
                }
            }    
        }
        br.close();
        resetTestFiles();
    }

    // Removes test files: example.txt, the corresponding blob, and the index entry
    private static void resetTestFiles() throws IOException, NoSuchAlgorithmException {
        // //Creates string with 10,000 a's
        // StringBuilder sb = new StringBuilder("");
        // for (int i=0; i<10000; i++) {
        //     sb.append("a");
        // }
        // //Deletes the blob file
        // try {
        //     Files.write(Paths.get("example.txt"), sb.toString().getBytes(StandardCharsets.UTF_8));
        // } catch (IOException e) {
        //     e.printStackTrace();
        // }
        // String sha1Hash = generateSha1(sb.toString());
        // Path blobFile = Paths.get("git/objects", sha1Hash);
        // Files.deleteIfExists(blobFile);
        // System.out.println("Deleted blob file: " + blobFile.toString());

        //Removes the line in the index file
        File indexFile = new File("git/index");
        if (indexFile.exists()) {
            indexFile.delete();
            indexFile.createNewFile();
            System.out.println("Removed the entry from the index file");
        }

        // //Deletes the example.txt file
        // Path exampleFile = Paths.get("example.txt");
        // Files.deleteIfExists(exampleFile);
        // System.out.println("Deleted example.txt");

        // //deletes the tree file
        // Path tree = Paths.get("tree");
        // Files.deleteIfExists(tree);

        //deletes everything in the objects directory
        File objects = new File ("git/objects");
        File [] fileList = objects.listFiles();
        for (int i =0;i<fileList.length;i++){
            fileList[i].delete();
        }

        File git = new File ("./git");
        git.delete();
        Git newGit = new Git();
        newGit.initGitRepo();
    }
}

//Creates custom error message
class ObjectsDirectoryNotFoundException extends Exception {
    public ObjectsDirectoryNotFoundException(String message) {
        super(message);
    }
}

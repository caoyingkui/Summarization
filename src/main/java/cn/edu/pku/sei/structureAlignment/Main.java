package cn.edu.pku.sei.structureAlignment;

import cn.edu.pku.sei.structureAlignment.CodeLineRelation.CodeLineRelationGraph;
import cn.edu.pku.sei.structureAlignment.feature.*;
import cn.edu.pku.sei.structureAlignment.paralellCorpusExtractor.SOExtractor;
import cn.edu.pku.sei.structureAlignment.parser.code.CodeVisitor;
import cn.edu.pku.sei.structureAlignment.parser.nlp.Dependency;
import cn.edu.pku.sei.structureAlignment.parser.nlp.NLParser;
import cn.edu.pku.sei.structureAlignment.result.Result;
import cn.edu.pku.sei.structureAlignment.result.ResultItem;
import cn.edu.pku.sei.structureAlignment.tree.*;
import cn.edu.pku.sei.structureAlignment.tree.node.Node;
import cn.edu.pku.sei.structureAlignment.tree.node.NodeType;
import cn.edu.pku.sei.structureAlignment.util.*;
import edu.stanford.nlp.simple.Sentence;
import org.eclipse.jdt.core.dom.*;

import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.util.Pair;


/**
 * Created by oliver on 2017/12/25.
 */
public class Main {
    private static int globalTotal = 0;
    private static int globalRight = 0;
    private static int globalWrong = 0;
    private static int codeLineCount = 0;
    private static int commentCount = 0;

    private static Result noControlResult ;
    private static Result controlResult;
    private static Result result;

    private static Map<CodeStructureTree , String> alignment = new HashMap<>();
    private static Map<CodeStructureTree , Integer> alignmentNum = new HashMap<>();
    private static Map<CodeStructureTree , Integer> addCommentCount = new HashMap<>();
    private static Map<CodeStructureTree , StringBuilder> addComments = new HashMap<>();

    public static void main(String[] args) throws IOException {

        noControlResult = new Result();
        controlResult = new Result();
        result = new Result();
        match(new File("code.txt"));
        /*extract();*/


        //match(new File("C:\\Users\\oliver\\Desktop\\数据\\no control sentence\\118.txt"));
        //match(new File("C:\\Users\\oliver\\Desktop\\数据\\contianning control sentence\\108.txt"));
        /*match(new File("C:\\Users\\oliver\\Desktop\\数据\\no control sentence\\199.txt"));
        match(new File("C:\\Users\\oliver\\Desktop\\数据\\no control sentence\\191.txt"));

        //addComment();
        /*for(CodeStructureTree codeTree : alignment.keySet()){
            boolean r = compare(codeTree , codeTree);
            System.out.println(r);
        }*/

        //match(new File("C:\\Users\\oliver\\Desktop\\test code snippets\\486.txt"));

        //match(new File("C:\\Users\\oliver\\Desktop\\数据\\contianning control sentence\\348.txt"));



        //File d = new File(".\\数据\\contianning control sentence");

        //File d = new File(".\\数据\\no control sentence");
        //File d = new File("C:\\Users\\oliver\\Desktop\\数据\\cook book");
        //File d = new File("C:\\Users\\oliver\\Desktop\\数据\\stackoverflow");
        //File d = new File("C:\\Users\\oliver\\Desktop\\test code snippets");


        /*
        File[] files = d.listFiles();
        if(files != null) {
            for (File file : files) {
                match(file);
                System.out.flush();
            }
        }*/

        /*d = new File("C:\\Users\\oliver\\Desktop\\数据\\contianning control sentence");
        //File d = new File("C:\\Users\\oliver\\Desktop\\数据\\cook book");
        //File d = new File("C:\\Users\\oliver\\Desktop\\数据\\stackoverflow");
        //File d = new File("C:\\Users\\oliver\\Desktop\\test code snippets");


        files = d.listFiles();
        if(files != null) {
            for (File file : files) {
                match(file);
                System.out.flush();
            }
        }*/

        double precision = (globalWrong + globalRight) == 0 ? 0 : (double) globalRight / (globalRight + globalWrong) ;
        double recall = globalTotal == 0 ? 1 : (double)globalRight / globalTotal;
        System.out.printf("total code lines:%d total comments:%d\n" , codeLineCount , commentCount);
        System.out.printf("total:%d right:%d wrong:%d precision:%.2f recall:%.2f\n\n\n", globalTotal , globalRight , globalWrong , precision , recall);
        System.out.printf("");
        result.print();

        /*for(CodeStructureTree corpusTree : alignment.keySet()){
            int treeNum = alignmentNum.get(corpusTree);

            BufferedWriter writer = new BufferedWriter(new FileWriter(new File("addCommentExample\\" + treeNum + ".txt") ));
            writer.write(corpusTree.getCode() + "\n");
            writer.write(alignment.get(corpusTree)+ "\n\n");
            writer.close();
        }

        addComment();*/
    }

    public static void compare(String codePath , String textPath){
        ArrayList<CodeStructureTree> codeTrees = new ArrayList<>();
        ArrayList<TextStructureTree> textTrees = new ArrayList<>();


        try{
            String line = "";

            CodeVisitor.initialize();
            BufferedReader reader = new BufferedReader(new FileReader(new File(codePath)));
            while((line = reader.readLine()) != null){
                ASTParser codeParser = ASTParser.newParser(AST.JLS8);
                codeParser.setKind(ASTParser.K_STATEMENTS);
                codeParser.setSource(line.toCharArray());
                Block block = (Block) codeParser.createAST(null);

                if(block != null || block.statements().size() == 0)
                    continue;
                CodeVisitor visitor = new CodeVisitor(0);
                ((ASTNode)(block.statements().get(0)) ).accept(visitor);
                CodeStructureTree tree = visitor.getTree();
                codeTrees.add(tree);
                //tree.print();
            }
            reader.close();


            line = "";
            NLParser textParser = null;
            reader = new BufferedReader(new FileReader(new File(textPath)));
            while((line = reader.readLine()) != null ) {
                textParser = new NLParser(line);
                TextStructureTree tree = textParser.getTextStructureTree();
                textTrees.add(tree);
            }

            Matrix<SimilarPair> matrix = new Matrix<>(codeTrees.size() , textTrees.size() , new SimilarPair(0 , 0));

            for(int i = 0 ; i < codeTrees.size() ; i ++){
                for(int j = 0 ; j < textTrees.size() ; j ++){
                    CodeStructureTree codeTree = codeTrees.get(i);
                    TextStructureTree textTree = textTrees.get(j);
                    SimilarPair pair = compare(codeTree , textTree);

                    matrix.setCell(i , j , pair);
                }
            }

            Pair<Integer , Integer> pair = null;
            do{
                //matrix.print(0);
                pair = matrix.getMax(0.5);
                if(pair != null){
                    int codeId = pair.getKey();
                    int textId = pair.getValue();
                    SimilarPair simiPair = matrix.getCell(codeId , textId);

                    matrix.cleanRow(codeId);
                    matrix.cleanColumn(textId);

                    CodeStructureTree scTree = (CodeStructureTree)codeTrees.get(codeId).getTree(simiPair.left);
                    TextStructureTree ssTree = (TextStructureTree)textTrees.get(textId).getTree(simiPair.right);

                    System.out.println(scTree.getCode().trim());
                    System.out.println(ssTree.getContent().trim() + "\n");


                }
            }while(pair != null);

        }catch (Exception e){
            e.printStackTrace();
        }

    }

    public static SimilarPair compare(CodeStructureTree codeTree , TextStructureTree textTree){

        double threshold = 0.5;

        int codeEndIndex = codeTree.getEndIndex();
        int textEndIndex = textTree.getEndIndex();
        Matrix<DoubleValue> similarMatrix = new Matrix(codeEndIndex + 1 , textEndIndex + 1 , new DoubleValue(-1));
        for(int i = 0 ; i <= codeEndIndex ; i ++)
            for(int j = 0 ; j <= textEndIndex ; j ++) {
                DoubleValue doubleValue = new DoubleValue(-1.0);
                similarMatrix.setCell(i, j, doubleValue);
            }

        findIdenticalPair(codeTree , textTree , similarMatrix);

        //similarMatrix.print(0);

        ArrayList<TextStructureTree> VPs = textTree.findAllVP();
        List<CodeStructureTree> codeNodes = null;

        for(TextStructureTree vpTree : VPs){
            Set<String> verbs = new HashSet<>();
            Set<String> directNouns = new HashSet<>();// 这部分名词，是在句法树中，明显能够分析得到的dobj关系的名词
            Set<String> normalNouns = new HashSet<>();// 这部分名词，就是出现在动词周围的名词，但是没有找到dobj关系

            //获取所有的动词，个人感觉一个VP中只会出现一个动词吧！？？
            List<TextStructureTree> verbNodes = vpTree.findAllVerb();
            for(TextStructureTree verbNode : verbNodes){
                verbs.addAll(Stemmer.stem(verbNode.getContent()));
                for(Dependency dependency : verbNode.getDependency("direct object")){
                    directNouns.addAll(Stemmer.stem(dependency.getTarget()));
                }
            }

            normalNouns.addAll(Stemmer.stem(vpTree.findAllNoun()));

            for(String verb : directNouns){
                if(normalNouns.contains(verb))
                    normalNouns.remove(verb);
            }

            codeNodes = codeTree.getAllNonleafTrees();
            //codeNodes = codeTree.getSpecificTypeNode(NodeType.CODE_MethodInvocation);
            if(verbs.contains("creat")) {
                codeNodes.addAll( codeTree.getSpecificTypeNode(NodeType.CODE_ClassInstanceCreation) );
                verbs.add("new");
            }

            double base = 1;

            for(CodeStructureTree codeNode : codeNodes){
                if(codeNode.getChildrenSize() < 2)//和一个动宾短语进行匹配，至少需要两个子节点嘛
                    continue;
                List<String> tokens = Stemmer.stem(codeNode.getContent());

                int signal = 0;
                for(String verb : verbs){
                    for(String token : tokens){
                        if( twoWordsAreSame(verb , token) ){
                            base = 0.5;
                            signal ++;
                            break;
                        }
                    }
                    if(signal == 1){
                        break;
                    }
                }

                if(directNouns.size() != 0){
                    for(String noun : directNouns){
                        if(tokens.contains(noun)){
                            base += 0.3;
                            break;
                        }
                    }
                }

                if(normalNouns.size() != 0){
                    for(String noun : normalNouns){
                        if(tokens.contains(noun)){
                            base += 0.1;
                            break;
                        }
                    }
                }

                if(base >= 0.5) {
                    int textId = vpTree.getId();
                    int codeId = codeNode.getId();
                    double sim = Stemmer.compare(vpTree.getContent() , codeNode.getContent());

                    if(base == 0.5) // 只出现了一个动词
                        sim = base + 0.1 * sim;
                    else if(base == 0.6) // 出现了普通名词
                        sim = base + 0.2 * sim;
                    else if(base == 0.8) // 出现了关键名词
                        sim = base + 0.1 * sim;
                    else if(base == 0.9) // 出现了普通名词和关键名词
                        sim = base + 0.1 * sim;

                    similarMatrix.setValue(codeId , textId , sim);
                }

            }
        }

        //similarMatrix.print(0);


        boolean signal = false;

        double max = 0.5;
        int max_codeId = -1;
        int max_textId = -1;
        for(int codeId = codeEndIndex  ; codeId > -1 ; codeId --){
            for(int textId = textEndIndex ; textId > -1  ; textId --){
                double simTemp = similarMatrix.getValue(codeId , textId);
                if( simTemp > max){
                    signal = true;
                    max_codeId = codeId;
                    max_textId = textId;
                    max = simTemp;
                }
            }
        }

        if(signal) {
            SimilarPair similarPair = new SimilarPair(max_codeId , max_textId );
            similarPair.setValue(max);
            return similarPair;
        }else
            return null;

    }

    public static void findIdenticalPair(CodeStructureTree codeTree , TextStructureTree textTree , Matrix matrix){


        Map<Integer , Integer> similarPairs = findIdenticalNodes(codeTree , textTree);

        // group stores the code nodes which have been found some identical text node in the text tree.
        ArrayList<Integer> group = new ArrayList<Integer>();
        for(int key : similarPairs.keySet()){
            group.add(key);
        }

        // find the nodes' common parent node
        Map<Integer , List<Integer>> parent_children = codeTree.findCommonParents( group , 20);

        for(int parent : parent_children.keySet()){
            List<Integer> children = parent_children.get(parent);
            Set<Integer> textChildren = new HashSet<>();

            for(int codeId : children){
                int textId = similarPairs.get(codeId);
                textChildren.add(textId);
                matrix.setValue(codeId , textId ,1);

            }

            int textParent = textTree.findCommonParents(textChildren);
            matrix.setValue(parent , textParent , 1);

            System.out.println(((CodeStructureTree)codeTree.getTree(parent)).getCode().trim());
            System.out.println(textTree.getTree(textParent).getContent().trim());
            System.out.println("   ");
        }

    }

    static Map<Integer , Integer> findIdenticalNodes(CodeStructureTree codeTree , TextStructureTree textTree){
        List<Node> codeLeafNodes = codeTree.getAllLeafNodes();
        List<Node> textLeafNodes = textTree.getAllLeafNodes();

        Map<Integer , Integer> similarPairs = new HashMap<Integer, Integer>();

        // this array is used to store the text node which have been recognized as identical with some code node
        ArrayList<Integer> textNodes = new ArrayList<>();
        int codeId , textId;
        for(Node codeNode : codeLeafNodes){
            codeId = codeNode.getId();
            for(Node textNode : textLeafNodes){
                textId = textNode.getId();
                if(Node.compare(codeNode, textNode, null , null) >= 0.5 && !textNodes.contains(textNode.getId())){
                    textNodes.add(textId);
                    similarPairs.put(codeId , textId);
                    break;
                }
            }
        }
        return similarPairs;
    }

    static double howWellAreTwoTreesAreSimilar(CodeStructureTree codeTree , TextStructureTree textTree , Map<String , Integer> tokenOccurFrequency){
        double result = 0;

        List<Node> codeNodes = codeTree.getAllLeafNodes();
        List<Node> textNodes = textTree.getAllLeafNodes();

        int codeLeafCount = codeNodes.size();
        int textLeafCount = textNodes.size();
        String text = String.join( " " , textTree.getContent().trim().split("[ ]+") );
        Matrix<DoubleValue> matrix = new Matrix<>(codeLeafCount , textLeafCount , new DoubleValue(0));
        for(int i = 0 ; i < codeLeafCount ; i ++){
            Node codeNode = codeNodes.get(i);
            NodeType nodeType = codeNode.getType();
            double factor = 2.0 / tokenOccurFrequency.get(codeNode.getContent());


            if(codeNode.getType() == NodeType.CODE_StringLiteral){
                String codeText = codeNode.getContent();
                codeText = codeText.length() > 2 ? codeText.substring(1 , codeText.length() - 1) : ""; // filter out the punctuation "
                if(codeText.trim().contains(" ")) {
                    codeText = String.join(" ", codeText.trim().split("[ ]+"));
                    if (text.contains(codeText)) {
                        result += (4 * codeText.trim().split("[ ]{1}").length);
                        matrix.cleanRow(i);
                        continue;
                    }
                }
            }else if(nodeType == NodeType.ADDED_CHAR_LEFT_PARENTHESIS ||
                    nodeType == NodeType.ADDED_CHAR_RIGHT_PARENTHESIS ||
                    nodeType == NodeType.ADDED_CHAR_LEFT_BRACKET ||
                    nodeType == NodeType.ADDED_CHAR_RIGHT_BRACKET ||
                    nodeType == NodeType.ADDED_CHAR_LEFT_BRACE ||
                    nodeType == NodeType.ADDED_CHAR_RIGHT_BRACE ||
                    nodeType == NodeType.ADDED_CHAR_COLON ||
                    nodeType == NodeType.ADDED_CHAR_COMMA ||
                    nodeType == NodeType.ADDED_CHAR_SEMICOLON ||
                    nodeType == NodeType.ADDED_CHAR_DOT ||
                    nodeType == NodeType.ADDED_CHAR_QUESTION){
                matrix.cleanRow(i);
                continue;
            }

            for(int j = 0 ; j < textLeafCount ; j ++){

                Node textNode = textNodes.get(j);
                double sim = Node.compare(codeNode, textNode, null , null) * factor;
                matrix.setValue(i , j , sim);
            }
        }

        /*Pair<Integer , Integer> max;
        while((max = matrix.getMax(0.01)) != null){
            int codeId = max.getKey();
            int textId = max.getValue();
            result += (4 * matrix.getCell(codeId , textId).getValue());
            matrix.cleanRow(codeId);
            matrix.cleanColumn( textId);
        }*/
        result = matrix.similarity(null);

        CreateClassFeature feature = new CreateClassFeature();
        if(feature.getFeature(textTree))
            result += feature.match(codeTree);

        KeyWordFeature keyWordFeature = new KeyWordFeature();
        if(keyWordFeature.getFeature(textTree))
            result += keyWordFeature.match(codeTree);

        /*MethodInvocationFeature methodInvocationFeature = new MethodInvocationFeature();
        if(methodInvocationFeature.getFeature(textTree))
            result += methodInvocationFeature.match(codeTree);*/
        return result;
    }

    static boolean twoWordsAreSame(String word1 , String word2){
        return word1.contains(word2)||word2.contains(word1);
    }

    public static List<Pair<Integer , Integer>> match(String codeString  , List<String> comments){

        //region <old version>
        /*
        //region <parsing code>
        CodeLineRelationGraph codeGraph = new CodeLineRelationGraph();
        codeGraph.build(codeString);
        List<CodeStructureTree> codeTrees = codeGraph.getCodeLineTrees();
        Matrix<DoubleValue> sliceMatrix = codeGraph.slicesMatrix;
        //endregion <parsing code>

        //region <parsing comment>
        List<TextStructureTree> textTrees = new ArrayList<>();
        List<List<Feature>> featureList = new ArrayList<>();
        for(String comment : comments){
            TextStructureTree textTree = new TextStructureTree(0);
            textTree.construct(new Sentence(comment));
            textTrees.add(textTree);

            //featureList.add(FeatureFactory.getFeatures(comment));
        }
        //endregion <parsing comment>

        int codeTreeCount = codeTrees.size();
        int textTreeCount = textTrees.size();

        Matrix<DoubleValue> matrix = new Matrix<>(codeTreeCount , textTreeCount , new DoubleValue(0));
        for(int i = 0 ; i < codeTreeCount ; i ++){
            CodeStructureTree codeTree = codeTrees.get(i);
            for(int j = 0 ; j < textTreeCount ; j ++){
                TextStructureTree textTree = textTrees.get(j);
                double sim = howWellAreTwoTreesAreSimilar(codeTree , textTree , codeGraph.tokenOccurFrequency);
                matrix.setValue(i , j , sim);
            }
        }
        matrix.print();

        List<Pair<Integer , Integer>> matchScheme = matrix.findBestMatchScheme(2);
        //matchScheme = textTreeCount > 8 ? matrix.findBestMatchScheme(2) :  matrix.findBestMatchScheme();



        List<Pair<Integer , Integer>> finalMatchScheme = updateMatchScheme(matchScheme ,sliceMatrix);


        return finalMatchScheme;*/
        //endregion <old version>
        return new SOExtractor().match(codeString , comments);
    }

    public static List<Pair<Integer , Integer>> updateMatchScheme(List<Pair<Integer , Integer>> matchScheme , Matrix<DoubleValue> sliceMatrix){
        List<Pair<Integer , Integer>> finalMatchScheme = new ArrayList<>();

        if(matchScheme != null) {
            List<Integer> codeHasMatchedToSomeText = new ArrayList<Integer>();
            for (Pair<Integer, Integer> pair : matchScheme) {
                codeHasMatchedToSomeText.add(pair.getKey());
            }

            int codeCount = sliceMatrix.getM();
            for (Pair<Integer, Integer> pair : matchScheme) {
                int code = pair.getKey();
                List<Integer> slice = new ArrayList<>();
                Collections.sort(slice, (o1 , o2) -> o1.compareTo(o2));

                int temp = code - 1;
                // temp + 1指向当前一行
                while (temp > -1 && sliceMatrix.getCell(temp + 1, temp).getValue() == 1) {
                    slice.add(temp);
                    temp--;
                }

                temp = code + 1;
                // temp - 1 指向当前一行
                while (temp < codeCount && sliceMatrix.getCell(temp - 1, temp).getValue() == 1) {
                    slice.add(temp);
                    temp++;
                }

                boolean s = false;
                for (Integer c : slice) {
                    if (codeHasMatchedToSomeText.contains((Object) c)) {
                        s = true;
                        break;
                    }
                }

                finalMatchScheme.add(new Pair<Integer, Integer>(code, pair.getValue()));
                if (!s) {
                    for (Integer c : slice) {
                        finalMatchScheme.add(new Pair<Integer, Integer>(c, pair.getValue()));
                    }
                }

            }
        }


        Collections.sort(finalMatchScheme , (pair1 , pair2) -> pair1.getKey().compareTo(pair2.getKey()) );

        return finalMatchScheme;
    }

    static List<Object> parseTestFile(File file){
        List<Object> result = new ArrayList<>();

        try{
            //region <read code>
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder codeString = new StringBuilder("");
            String line;
            while((line = reader.readLine()).length() != 0){
                codeString.append(line).append("\n");
                codeLineCount ++;
            }

            result.add(codeString.toString());
            //endregion <read code>


            // region <read comments>
            List<String> comments = new ArrayList<>();
            List<TextStructureTree> textTrees = new ArrayList<>();
            while((line = reader.readLine()).length() != 0){
                comments.add(line);
                codeString.append(line).append(" "); // for idf
                commentCount ++;
            }

            result.add(comments);
            // endregion <read comments>

            //region <read annotations>
            Map<Integer, List<Integer>> annotations = new HashMap<>();
            int codeLineNum;
            int commentNum;
            while((line = reader.readLine()).compareTo("END") != 0 ){
                String[] nums = line.split(" ");
                commentNum = Integer.parseInt(nums[1]);
                List<Integer> codes = new ArrayList<>();
                for(String codeNum : nums[0].split("\\|")){
                    codes.add(Integer.parseInt(codeNum));
                }
                annotations.put(commentNum , codes);
            }
            result.add(annotations);
            //endregion <read annotations>

        }catch (Exception e){
            e.printStackTrace();
        }

        return result;

    }

    /**
     * the file should be arranged as the following format:
     * codeLines
     *
     * comments
     *
     * results
     * @param file
     * @return
     */
    static double match(File file){

        System.out.println(file.getName() + ":");
        try{

            List<Object> metaInfo = parseTestFile(file);

            //region <data processing>
            String codeString = (String)metaInfo.get(0);
            List<String> comments = (List<String>) metaInfo.get(1);
            Map<Integer, List<Integer>> annotations = (Map<Integer , List<Integer>>) metaInfo.get(2);
            //endregion <data processing>

            //LSTM.currentFile = file.getName().replace(".txt", "");


            List<Pair<Integer , Integer>> finalMatchScheme = match(codeString ,comments );
            analysesResult(finalMatchScheme , annotations);

            //CodeLineRelationGraph codeGraph = new CodeLineRelationGraph();
            //codeGraph.build(codeString);
            //codeCommentAlignment(finalMatchScheme , codeGraph , comments);

        }catch(Exception e){
            e.printStackTrace();
        }

        return 0;
    }


    /**
     * codeCommentAlignment函数是用于在生成对齐结果后，分析其中代码和文本的对齐的节点
     * 把注释的对应的节点内容，替换成对应代码节点的编号
     * @param finalMatchScheme
     * @param graph
     * @param comments
     */
    static void codeCommentAlignment(List<Pair<Integer , Integer>>finalMatchScheme , CodeLineRelationGraph graph , List<String> comments){
        Map<Integer , List<Integer>> comment2Codes = new HashMap<>();
        for(Pair<Integer , Integer> pair : finalMatchScheme){
            int codeNum = pair.getKey();
            int commentNum = pair.getValue();

            if(comment2Codes.containsKey(commentNum)){
                comment2Codes.get(commentNum).add(codeNum);
            }else{
                List<Integer> codes = new ArrayList<>();
                codes.add(codeNum);
                comment2Codes.put(commentNum , codes);
            }
        }

        for(Integer commentNum : comment2Codes.keySet()){
            List<Integer> codes = comment2Codes.get(commentNum);
            if(codes.size() == 1){
                CodeStructureTree codeTree = graph.getCodeLineTrees().get(codes.get(0));
                String comment = comments.get(commentNum);
                TextStructureTree textTree = new TextStructureTree(0);
                textTree.construct(new Sentence(comment));

                List<Node> codeNodes = codeTree.getAllLeafNodes();
                List<Node> textNodes = textTree.getAllLeafNodes();

                // text-node    code-node
                Map<Integer ,Integer> identicalPairs = new HashMap<>();

                for(int i = 0 ; i < codeNodes.size() ; i ++){
                    for(int j = 0 ; j < textNodes.size() ; j++){
                        double sim = Node.compare(codeNodes.get(i), textNodes.get(j), null , null);
                        if(sim > 0.8){
                            identicalPairs.put(j , i);
                        }
                    }
                }

                boolean signal = false;
                if(identicalPairs.size() > 0)
                    signal = true;
                for(Integer textNodeNum : identicalPairs.keySet()){
                    int codeNodeNum = identicalPairs.get(textNodeNum);
                    textNodes.get(textNodeNum).setContent("#" + codeNodes.get(codeNodeNum).getId());
                    textNodes.get(textNodeNum).setDisplayContent("#" + codeNodes.get(codeNodeNum).getId());

                }
                String alignmentComment = "";
                if(signal) {
                    for (Node node : textNodes) {
                        alignmentComment += (node.getContent() + " ");
                    }
                }else{
                    alignmentComment = textTree.getContent();
                }

                alignment.put(codeTree , alignmentComment);
                alignmentNum.put(codeTree , alignmentNum.size());
                addCommentCount.put(codeTree , 0);
                addComments.put(codeTree , new StringBuilder(codeTree.getCode() + "\n" + alignmentComment + "\n\n--------------------"));
            }
        }
    }

    private static void outputSingleResult(List<Pair<Integer , Integer>> matchScheme , Map<Integer, List<Integer>> annotations){
        int codeLineNum = 0 , commentNum = 0 ;
        int right = 0, wrong = 0;
        if(matchScheme != null) {
            for (Pair<Integer, Integer> pair : matchScheme) {
                codeLineNum = pair.getKey() + 1;
                commentNum = pair.getValue() + 1;
                if (annotations.containsKey(commentNum) && annotations.get(commentNum).contains(codeLineNum)) {
                    right++;
                    System.out.print("  right:");
                } else {
                    wrong++;
                    System.out.print("  wrong:");
                }
                System.out.println((codeLineNum ) + " " + (commentNum ));
            }
        }

        int total = 0;

        for(Integer commentLine : annotations.keySet()){
            total += annotations.get(commentLine).size();
        }

        globalTotal += total;
        globalRight += right;
        globalWrong += wrong;
        double precision = (wrong + right) == 0 ? 0 : (double) right / (right + wrong) ;
        double recall = total == 0 ? 1 : (double)right / total;
        System.out.printf("total:%d right:%d wrong:%d precision:%.2f recall:%.2f\n\n\n", total , right , wrong , precision , recall);

    }

    private static void analysesResult(List<Pair<Integer , Integer>> matchScheme , Map<Integer, List<Integer>> annotations ){

        outputSingleResult(matchScheme , annotations);


        Map<Integer , Integer> code2comment = new HashMap<Integer , Integer> ();
        for(int commentNum : annotations.keySet()){
            for(Integer code : annotations.get(commentNum)){
                code2comment.put(code , commentNum);
            }
        }

        Map<Integer , List<Integer>> scheme = new HashMap<>();
        for(Pair<Integer , Integer> pair : matchScheme){
            int codeLine = pair.getKey() + 1;
            int commentLine = pair.getValue() + 1;

            if(scheme.containsKey(commentLine)){
                scheme.get(commentLine).add(codeLine);
            }else{
                List<Integer> temp = new ArrayList<>();
                temp.add(codeLine);
                scheme.put(commentLine , temp);
            }
        }

        for(int comment : annotations.keySet()){
            List<Integer> annotation = annotations.get(comment);
            int size = annotation.size();

            ResultItem item ;
            if(result.items.containsKey(size)){
                item = result.items.get(size);
            }else{
                item = new ResultItem();
                result.items.put(size , item);
            }
            item.exampleCount ++;

            if(scheme.containsKey(comment)){
                List<Integer> matchResult = scheme.get(comment);

                boolean wronglyMatch = false;
                for(Integer code : matchResult){

                    if(code2comment.containsKey(code) && code2comment.get(code) != comment){
                        wronglyMatch = true;
                        break;
                    }
                }

                if(wronglyMatch){
                    item.wronglyMatch  ++;
                }else{

                    boolean completely = true;
                    int count = 0;
                    for(int code : annotation){
                        if(!matchResult.contains(code)){
                            completely = false;
                        }else{
                            count ++;
                        }
                    }

                    if(completely && count > 0){
                        item.completelyMatch ++;
                    }else if(!completely && count > 0){
                        item.partlyMatch ++;
                    }else if(count == 0){
                        item.wronglyMatch ++;
                    }
                }

            }else{
                item.noMatch ++;
            }
        }
    }

    static boolean compare(CodeStructureTree tree1 , CodeStructureTree tree2){
        boolean result = true;
        List<CodeStructureTree> children1 = tree1.getChildren();
        List<CodeStructureTree> children2 = tree2.getChildren();
        if(children1.size() != children2.size())
            return false;
        else if(children1.size() == 0){
            if(tree1.root.getType() == NodeType.ADDED_METHOD_NAME){
                if(tree2.root.getType() == NodeType.ADDED_METHOD_NAME &&
                        tree1.getContent().compareTo(tree2.getContent()) == 0)
                    return true;
                else
                    return false;
            }else
                return true;
        }else{
            if(tree1.root.getType() != tree2.root.getType()){
                return false;
            }

            if(tree1.root.getType() == NodeType.CODE_SimpleType){
                String type1 = children1.get(0).getCode();
                String type2 = children2.get(0).getCode();
                if(type1.compareTo(type2) != 0)
                    return false;
                else
                    return true;
            }

            for(int i = 0 ; i < children1.size() ; i ++){
                result = compare(children1.get(i) , children2.get(i));
                if(!result)
                    return false;
            }

            return true;
        }
    }

    public static void addComment(){
        List<String> files = findAllJavaFile(new File("D:\\test\\lucene5-master\\src\\main\\java\\com\\yida\\framework\\lucene5\\hightlight\\"));
        //List<String> files = findAllJavaFile(new File("D:\\test"));
        boolean stop = false;

        ASTParser parser = ASTParser.newParser(AST.JLS8);
        for(String fileName : files){
            File file = new File(fileName);
            try{
                FileInputStream in = new FileInputStream(file);
                byte[] bytes = new byte[in.available()];
                in.read(bytes);
                parser.setSource(new String(bytes).toCharArray());
                parser.setKind(ASTParser.K_COMPILATION_UNIT);

                CompilationUnit unit = (CompilationUnit)parser.createAST(null);
                if(unit != null ){
                    List<AbstractTypeDeclaration> types = unit.types();

                    for(AbstractTypeDeclaration type : types){
                        if(type instanceof TypeDeclaration){
                            MethodDeclaration[] methods = ((TypeDeclaration) type).getMethods();

                            for(MethodDeclaration method : methods){
                                CodeLineRelationGraph graph = new CodeLineRelationGraph();

                                graph.build(method.getBody());
                                if(graph.getCodeLineTrees().size() == 0)
                                    continue;


                                stop = addComment(graph.getCodeLineTrees() , file.getAbsolutePath());
                                if(stop)
                                    break;
                            }
                        }
                    }

                    if(stop)
                        break;
                }


            }catch (Exception e){

            }

            if(stop)
                break;
        }

    }

    /**
     *
     * @param codeTrees
     * @param filePath
     * @return 当返回true的时候，表示已经为所有的数据，生成了额定数量的例子，则将停止继续遍历文件。
    */
    private static boolean addComment(List<CodeStructureTree> codeTrees , String filePath){


        int count = 0 ; // 用于在遍历时，记录已经为count个例子，生成了足够的数据。

        for(CodeStructureTree corpusCodeTree : alignment.keySet()){
            int treeNum = alignmentNum.get(corpusCodeTree);
            int howMany = addCommentCount.get(corpusCodeTree);

            if(howMany > 5) {
                count++;
                continue;
            }

            String comment = alignment.get(corpusCodeTree);
            Pattern pattern = Pattern.compile("#([1-9][0-9]*) ");
            Matcher matcher = pattern.matcher(comment);
            List<Integer> replacements = new ArrayList<Integer>();
            while(matcher.find()){
                replacements.add(Integer.parseInt(matcher.group(1)));
            }

            for(CodeStructureTree repertoryCodeTree : codeTrees){

                if( compare(corpusCodeTree , repertoryCodeTree)){
                    String result = comment;
                    for(Integer replacement : replacements){
                        result = result.replace("#" + replacement , repertoryCodeTree.getNode(replacement).getContent());
                    }

                    try {
                        BufferedWriter writer = new BufferedWriter(new FileWriter(new File("addCommentExample\\" + treeNum + ".txt"), true));
                        writer.write("****************************\n");
                        writer.write(filePath + "\n\n");
                        writer.write(repertoryCodeTree.getCode() + "\n\n");
                        writer.write(result + "\n");
                        writer.write("****************************\n\n\n");
                        writer.close();
                    }catch (Exception e){
                        e.printStackTrace();
                    }

                    System.out.println(filePath);

                    addCommentCount.put(corpusCodeTree, addCommentCount.get(corpusCodeTree) + 1);
                }
            }
        }

        return count == alignment.size();

    }

    public static List<String> findAllJavaFile(File directory){
        List<String> result = new ArrayList<>();

        File[] files = directory.listFiles();

        for(File file : files){
            if(file.isDirectory()){
                result.addAll(findAllJavaFile(file));
            }else if(file.isFile() && file.getName().lastIndexOf(".java") == file.getName().length() - 5){
                result.add(file.getAbsolutePath());
            }
        }

        return result;
    }

    public static void extract(){
        List<File> files = new ArrayList<>();

        for(File f : new File("C:\\Users\\oliver\\Desktop\\数据\\contianning control sentence").listFiles())
            files.add(f);

        for(File f : new File("C:\\Users\\oliver\\Desktop\\数据\\no control sentence").listFiles())
            files.add(f);

        for(File f : files){
            String name = f.getName();
            name = name.replace(".txt", "");

            List<Object> metaInfo = parseTestFile(f);

            String codeString = (String)metaInfo.get(0);
            List<String> comments = (List<String>) metaInfo.get(1);
            Map<Integer, List<Integer>> annotations = (Map<Integer , List<Integer>>) metaInfo.get(2);

            CodeLineRelationGraph graph = new CodeLineRelationGraph();
            graph.build(codeString);

            try {
                BufferedWriter methWriter = new BufferedWriter(new FileWriter(new File("D:\\lstm data\\methname\\" + name)));
                BufferedWriter apiseqWriter = new BufferedWriter(new FileWriter(new File("D:\\lstm data\\apiseq\\" + name)));
                BufferedWriter tokensWriter = new BufferedWriter(new FileWriter(new File("D:\\lstm data\\tokens\\" + name)));
                BufferedWriter descWriter = new BufferedWriter(new FileWriter(new File("D:\\lstm data\\desc\\" + name)));

                for (CodeStructureTree tree : graph.getCodeLineTrees()) {
                    Map<String, String> info = tree.generate();
                    methWriter.write(info.get("methname") + "\n");
                    apiseqWriter.write(info.get("apiseq") + "\n");
                    tokensWriter.write(info.get("tokens") + "\n");
                }

                methWriter.close();
                apiseqWriter.close();
                tokensWriter.close();

                for(String comment : comments)
                    descWriter.write(comment + "\n");
                descWriter.close();

            }catch(Exception e){
                ;
            }

        }
    }

}

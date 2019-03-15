package cn.edu.pku.sei.structureAlignment.tree.node;

import cn.edu.pku.sei.structureAlignment.parser.code.CodeVisitor;
import cn.edu.pku.sei.structureAlignment.tree.MatchedNode;
import cn.edu.pku.sei.structureAlignment.tree.Tree;
import cn.edu.pku.sei.structureAlignment.util.Stemmer;
import cn.edu.pku.sei.structureAlignment.util.StopWordList;
import cn.edu.pku.sei.structureAlignment.util.WN;
import edu.cmu.lti.lexical_db.ILexicalDatabase;
import edu.cmu.lti.lexical_db.NictWordNet;
import edu.cmu.lti.ws4j.impl.WuPalmer;
import javafx.util.Pair;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
import edu.cmu.lti.ws4j.util.*;
import org.python.core.PyFunction;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.util.PythonInterpreter;
*/
/**
 * Created by oliver on 2017/12/23.
 */
public class Node {
    public int id;
    public NodeType type;
    protected String content;  // the original text of a node
    public List<String> alternatives;
    protected String displayContent; //
    protected String additionalInfo; // the information we can be extracted by other ways.


    // maxSimilarity用于在匹配节点时，如果maxSimilarity的值为1，就说明以前存在有完全匹配成功节点，那么部分相似的节点将不会加入matchedCodeNodeList
    // 如果maxSimilarity的值小于1，说明前面匹配的节点都是部分相似的节点，如果匹配到完全相似的节点，那就把以前的匹配成功的节点全部清零。
    // node中有maxSimilarity
    public double maxSimilarity = 0;


    //matchedCodeNodeList 是用与记录当前节点的匹配历史
    //map中的key，表示当前节点和k值指向的树存在相似或相同的节点，value存储的是list，表示树中匹配成功的节点编号
    public List<MatchedNode> matchedCodeNodeList ;


    //region <setter>
    public void setType(NodeType type) {
        this.type = type;
    }

    public void setContent(String content) {
        this.content = content;
        //this.displayContent = content;
    }

    public void addAlternatives(String content){
        alternatives.add(content.toLowerCase());
    }

    public void setDisplayContent(String displayContent){
        this.displayContent = displayContent;
    }

    public void setAdditionalInfo(String additionalInfo){
        this.additionalInfo += additionalInfo + " ";
    }

    //endregion <setter>


    //region <getter>
    public int getId(){
        return id;
    }

    public NodeType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getDisplayContent(){
        return displayContent;
    }

    public String getAdditionalInfo(){
        return additionalInfo;
    }
    //endregion <getter>

    public Node(NodeType type , String content , int id){
        this.type = type;
        this.content = content;
        this.displayContent = content;
        this.id = id;

        alternatives = new ArrayList<>();
        String typeInfo = CodeVisitor.getVariableType(content);
        if(typeInfo.compareTo("") != 0) {
            alternatives.add(typeInfo);
        }

        this.additionalInfo = "";
        matchedCodeNodeList = new ArrayList<>();
    }

    /**
     * @param
     * @return 2:  if two nodes' are completely identical and the content are not a class name.
     *              I think this condition will be more reliable to predict two nodes are identical
     *          1: if two nodes' are completely identical and the content are a class name
     *          0.5: if two nodes' are partially identical
     *          0: if two nodes' are completely different
     */
    public static double compare(Node codeNode, Node textNode, Map<String , Integer> codeTokenOccurFrequency, Map<String , Integer> textTokenOccurFrequency){

        if (codeNode.type == NodeType.ADDED_KEYWORD)
            return 0;

        // 默认this指向的是一个code节点
        String content = codeNode.getContent();
        String anotherContent = textNode.getContent();



        int frequency = 1;
        if(codeTokenOccurFrequency != null && textTokenOccurFrequency != null)
            frequency = Math.max(codeTokenOccurFrequency.getOrDefault(content, 1),
                textTokenOccurFrequency.getOrDefault(anotherContent, 1));
        double result = 0;
        //remove the punctuation "
        if(codeNode.type == NodeType.CODE_StringLiteral){
            //content = content.length() > 2 ? content.substring(1 , content.length() - 1) : "";
            Pattern pattern = Pattern.compile("[a-zA-Z0-9]");
            Matcher matcher = pattern.matcher(content);
            if(!matcher.find()) // 例如content的内容为 "."
                return 0;
        }

        if(content.trim().toLowerCase().compareTo(anotherContent.trim().toLowerCase()) == 0){
            result =  1.0 + (codeNode.type == NodeType.ADDED_METHOD_NAME ? 1:0 );
        }else if (codeNode.alternativesContains(anotherContent) ){
            result = 1.0;
        }else if(!(content.contains(" ") && anotherContent.contains(" "))){
            if(Stemmer.stemSingleWord(content).compareTo(Stemmer.stemSingleWord(anotherContent)) == 0){
                result = 1.0;
            }
        }

        if(result == 0) {
            String camelCasePattern = "([^\\p{L}\\d]+)|(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)|(?<=[\\p{L}&&[^\\p{Lu}]])(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}[\\p{L}&&[^\\p{Lu}]])";
            String[] subs1 = content.split(camelCasePattern);
            String[] subs2 = anotherContent.split(camelCasePattern);
            double sim = 0.0;
            try {
                for (String sub1 : subs1) {
                    for (String sub2 : subs2) {
                        if(sub1.length() > 2 && sub2.length() > 2) {
                            if (sub1.startsWith(sub2) || sub2.startsWith(sub1)) {
                                result = 0.5;
                                break;
                            }
                        }
                        double temp = WN.getSimilarity(sub1.toLowerCase(), sub2.toLowerCase());
                        if (temp > sim)
                            sim = temp;
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (sim >= 0.5) {
                return sim;
            }
        }

        if(result == 0) {
            Set<String> words1 = new HashSet<String>();
            words1.addAll(Stemmer.stem(content + " " + codeNode.getAdditionalInfo()));

            Set<String> words2 = new HashSet<String>();
            words2.addAll(Stemmer.stem(anotherContent + " " + textNode.getAdditionalInfo()));

            for (String word1 : words1) {
                for (String word2 : words2) {
                    if (word1.compareTo(word2) == 0) {
                        if(textNode.isNoun() || textNode.isVerb())
                            return 1;
                        /*if (textNode.isNoun())
                            return 0.75;
                        else if (textNode.isVerb())
                            return 0.5;*/
                        else
                            return 0.25;

                    }
                }
            }
        }
        return  result ; //result * 2 / frequency;
    }

    /**
     * @param node
     * @return 2:  if two nodes' are completely identical and the content are not a class name.
     *              I think this condition will be more reliable to predict two nodes are identical
     *          1: if two nodes' are completely identical and the content are a class name
     *          0.5: if two nodes' are partially identical
     *          0: if two nodes' are completely different
     */
    public double compare(Node node , Map<String , Integer> tokenOccurFrequency){

        String content = this.getContent();
        String anotherContent = node.getContent();
        System.out.println("afads");

        if(content.compareTo("Verify") == 0 && anotherContent.compareTo("assertEquals") == 0){
            int a = 1;
        }


        //remove the punctuation "
        if(this.type == NodeType.CODE_StringLiteral) content = content.length() > 2 ? content.substring(1 , content.length() - 1) : "";

        if(node.type == NodeType.CODE_StringLiteral) anotherContent = anotherContent.length() > 2 ? content.substring(1 , content.length() - 1) : "" ;

        content = content.trim();
        anotherContent = content.trim();

        if(content.trim().toLowerCase().compareTo(anotherContent.trim().toLowerCase()) == 0 || this.alternativesContains(anotherContent)) {
            return 2.0 / tokenOccurFrequency.getOrDefault(anotherContent , 1);
        }



        Set<String> words1 = new HashSet<String>();
        words1.addAll(Stemmer.stem(content + " " + this.getAdditionalInfo()));

        Set<String> words2 = new HashSet<String>();
        words2.addAll(Stemmer.stem(anotherContent + " " + node.getAdditionalInfo()));

        for(String word1 : words1){
            for(String word2 : words2 ){
                if(word1.compareTo(word2) == 0){
                    return 0.5 / tokenOccurFrequency.getOrDefault(anotherContent , 1);
                }
            }
        }
        return 0;
    }

    public boolean alternativesContains(String content){
        content = content.trim().toLowerCase();
        return alternatives.contains(content);
    }

    /**
     * Add new matched node of this node,
     * In this process, I need to guarantee 2 things:
     * First, the newNode match this node with the highest similarity
     * Second, for the newNode, it should guarantee this node match the newNode with the highest similarity.
     * So, assume the similarity of this node and newNode is 0.5 , there will be several special situation:
     * 1, this node has been matched to another node with 0.6 similarity.
     * 2, this node has been matched to another node with 0.5 similarity.
     * 3, this node has been matched to another node with 0.4 similarity.
     * 4, the newNode has been matched to another node with 0.6 similarity.
     * 5, the newNode has been matched to another node with 0.5 similarity.
     * 6, the newNode has been matched to another node with 0.4 similarity.
     * if 1 or 4 happens, this function will not add this newNode
     * @param newNode
     * @return true, if succeed in adding.
     *          false , else
     */
    public boolean addMatchedNode(MatchedNode newNode){
        double sim = newNode.similarity;
        if(newNode.textNode == null){
            return false;
        }
        try {
            if (sim >= newNode.textNode.maxSimilarity &&
                    sim >= newNode.codeNode.maxSimilarity) {
                if (this.matchedCodeNodeList == null || this.maxSimilarity < sim)
                    this.matchedCodeNodeList = new ArrayList<MatchedNode>();

                this.maxSimilarity = sim;
                this.matchedCodeNodeList.add(newNode);
                return true;
            } else
                return false;
        }catch (Exception e){
            return false;
        }

    }

    public List<MatchedNode> merge(List<List<MatchedNode>> childrenMatchedNodes , int sourceTreeNum , List<? extends Tree> targetTrees){
        if(matchedCodeNodeList == null)
            matchedCodeNodeList = new ArrayList<>();
        else
            matchedCodeNodeList.clear();

        Map<Integer , Pair<Set<Integer> , Double>> tempMergingNodes = getMergingNodes(childrenMatchedNodes);
        Map<Integer , Pair<Set<Integer> , Double>> mergingNodes = new HashMap<>();

        double maxSimilarity = -1;
        for(Integer treeNum : tempMergingNodes.keySet()){
            Pair<Set<Integer> , Double> pair = tempMergingNodes.get(treeNum);
            double similarity = pair.getValue();
            if(similarity > maxSimilarity){
                maxSimilarity = similarity;
                mergingNodes.clear();
                mergingNodes.put(treeNum , pair);
            }else if(similarity == maxSimilarity){
                mergingNodes.put(treeNum , pair);
            }
        }

        for(Integer treeNum : mergingNodes.keySet()){
            Pair<Set<Integer> , Double> pair = mergingNodes.get(treeNum);
            Set<Integer> nodes = pair.getKey();
            double mergedSimilarity = pair.getValue() + 2; // 1是奖励项

            Tree tree = targetTrees.get(treeNum);
            int mergedNodeNum = tree.findCommonParents(nodes);
            Node targetNode = tree.getNode(mergedNodeNum);

            MatchedNode newNode = new MatchedNode(treeNum, targetNode, sourceTreeNum , this, mergedSimilarity );
            matchedCodeNodeList.add(newNode);
            targetNode.addMatchedNode(newNode);

        }
        return matchedCodeNodeList.size() > 0 ? matchedCodeNodeList : null;
    }

    /**
     * Given childrenMatchedNodes, the each element of this list store all the matched nodes , all these matched nodes are matched to one node of the one subTree
     * We want to find the node list which can be merged
     * For example , assume childrenMatchedNodes is { {<1 , 5>, <3 , 4> , <1 , 6>} , {<3 , 8> , <4 , 5>}}
     * this means one subTree has matched to 5th node of 1st tree  , 4th node of  3rd tree' and  6th node of 1st tree , and another subTree has matched to 8th node of 3rd tree and 5th node of 4th tree.
     * there will be 3 special situation:
     * 1, 4th tree ,there is only one match node , it can't not been merged
     * 2, 3rd tree , there are 2 nodes from 2 sub tree , it can be merged
     * 3, 1st tree , although there are 2 nodes, all are from one same subTree, this two nodes will not be merged, and these situation usually happen when match leaf nodes
     * @param childrenMatchedNodes
     * @return
     */
    public Map<Integer , Pair<Set<Integer> , Double>> getMergingNodes(List<List<MatchedNode>> childrenMatchedNodes){
        Map<Integer , Integer> treeOccurTimes = new HashMap<>(); // the number of a target subTree occurs in different source subTree
        Map<Integer , Set<Integer>> nodesFromSameTree = new HashMap<>(); //
        Map<Integer , Double> similarityAfterMerge = new HashMap<>();

        for(List<MatchedNode> matchedNodes : childrenMatchedNodes){
            Set<Integer> occurTemp = new HashSet<>();

            for(MatchedNode matchedNode : matchedNodes){
                int matchedTreeID = matchedNode.codeTreeID;
                int matchedNodeID = matchedNode.codeNode.getId();
                double similarity = matchedNode.similarity;

                occurTemp.add(matchedTreeID);

                Set<Integer> matchedNodeFromThisTree ;
                if(nodesFromSameTree.containsKey(matchedTreeID)){
                    matchedNodeFromThisTree = nodesFromSameTree.get(matchedTreeID);
                    matchedNodeFromThisTree.add(matchedNodeID);
                    similarityAfterMerge.put(matchedTreeID , similarityAfterMerge.get(matchedTreeID) + similarity);
                }else{
                    matchedNodeFromThisTree = new HashSet<>();
                    matchedNodeFromThisTree.add(matchedNodeID);
                    nodesFromSameTree.put(matchedTreeID , matchedNodeFromThisTree);
                    similarityAfterMerge.put(matchedTreeID , similarity);
                }

            }

            for(int tree : occurTemp){
                if(treeOccurTimes.containsKey(tree)){
                    treeOccurTimes.put(tree , treeOccurTimes.get(tree) + 1 );
                }else{
                    treeOccurTimes.put(tree , 1);
                }
            }
        }

        Map<Integer , Pair<Set<Integer> , Double>> result = new HashMap<>();

        for(int tree : treeOccurTimes.keySet() ){
            if(treeOccurTimes.get(tree) > 1 && nodesFromSameTree.get(tree).size() > 1){
                result.put(
                        tree ,
                        new Pair<Set<Integer> , Double>(nodesFromSameTree.get(tree) , similarityAfterMerge.get(tree))
                );
            }
        }

        return result;
    }

    public  boolean isPunctuation(){
        return type == NodeType.ADDED_CHAR_LEFT_PARENTHESIS ||
                type == NodeType.ADDED_CHAR_RIGHT_PARENTHESIS ||
                type == NodeType.ADDED_CHAR_LEFT_BRACKET ||
                type == NodeType.ADDED_CHAR_RIGHT_BRACKET ||
                type == NodeType.ADDED_CHAR_LEFT_BRACE ||
                type == NodeType.ADDED_CHAR_RIGHT_BRACE ||
                type == NodeType.ADDED_CHAR_COLON ||
                type == NodeType.ADDED_CHAR_COMMA ||
                type == NodeType.ADDED_CHAR_SEMICOLON ||
                type == NodeType.ADDED_CHAR_DOT ||
                type == NodeType.ADDED_CHAR_QUESTION;
    }

    public boolean isStopWord(){
        return StopWordList.contains(content);
    }

    public boolean isVerb(){
        return type == NodeType.TEXT_VB  ||
                type == NodeType.TEXT_VBD ||
                type == NodeType.TEXT_VBG ||
                type == NodeType.TEXT_VBN ||
                type == NodeType.TEXT_VBP ||
                type == NodeType.TEXT_VBZ ;
    }

    public boolean isNoun(){
        return type == NodeType.TEXT_NN ||
                type == NodeType.TEXT_NNS ||
                type == NodeType.TEXT_NNP ||
                type == NodeType.TEXT_NNPS;
    }

    public static void main(String[] args){
        /*
        Properties props = new Properties();
        props.put("python.home", "path to the Lib folder");
        //props.put("python.console.encoding", "UTF-8");
        props.put("python.security.respectJavaAccessibility", "false");
        props.put("python.import.site", "false");
        Properties preprops = System.getProperties();
        PythonInterpreter.initialize(preprops, props, new String[0]);


        PythonInterpreter interpreter = new PythonInterpreter();
        interpreter.exec("import sys");
        interpreter.exec("sys.path.append('F:\\Python35\\Lib')");
        interpreter.exec("sys.path.append('F:\\Python35\\Lib\\site-packages')");
        interpreter.execfile("C:\\Users\\oliver\\PycharmProjects\\wordSimilarityTest\\test.py");
        PyFunction func = (PyFunction) interpreter.get("similarity_base_on_wordNet" , PyFunction.class);
        PyObject obj = func.__call__(new PyString("one") , new PyString("two"));
        System.out.println(obj.toString());*/


        /*ILexicalDatabase db = new NictWordNet();
        WS4JConfiguration.getInstance().setMFS(true);
        double s = new WuPalmer(db).calcRelatednessOfWords("test", "measure");
        System.out.println(s);
        /*String[] arguments = {"python", "C:\\Users\\oliver\\PycharmProjects\\wordSimilarityTest\\test.py" , "assert" , "verify"};
        try {
            //Process process = Runtime.getRuntime().exec(arguments);
            Process process = Runtime.getRuntime().exec("python C:\\Users\\oliver\\PycharmProjects\\wordSimilarityTest\\test.py assert verify");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            System.out.println(process.waitFor() + " " + line);

        }catch (Exception e){
            e.printStackTrace();
        }*/
    }


}




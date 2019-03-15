package cn.edu.pku.sei.structureAlignment.alignment;

import cn.edu.pku.sei.structureAlignment.CodeLineRelation.CodeLineRelationGraph;
import cn.edu.pku.sei.structureAlignment.feature.CreateClassFeature;
import cn.edu.pku.sei.structureAlignment.feature.KeyWordFeature;
import cn.edu.pku.sei.structureAlignment.feature.MethodInvocationFeature;
import cn.edu.pku.sei.structureAlignment.tree.CodeStructureTree;
import cn.edu.pku.sei.structureAlignment.tree.MatchedNode;
import cn.edu.pku.sei.structureAlignment.tree.node.Node;
import cn.edu.pku.sei.structureAlignment.tree.TextStructureTree;
import cn.edu.pku.sei.structureAlignment.util.DoubleValue;
import cn.edu.pku.sei.structureAlignment.util.LSTM;
import cn.edu.pku.sei.structureAlignment.util.Matrix;
import cn.edu.pku.sei.structureAlignment.util.WN;
import edu.stanford.nlp.simple.Sentence;
import javafx.util.Pair;

import java.util.*;

/**
 * Created by oliver on 2018/6/28.
 */
public class SimilarityMatrix {
    public String codeString;
    public List<CodeStructureTree> codeTrees;
    public List<String> comments;
    public List<TextStructureTree> textTrees;
    public Matrix<DoubleValue> matrix;
    public Matrix<DoubleValue> sliceMatrix;

    public Map<String , Integer> codeTf ;
    public Map<String , Integer> textTf;

    public SimilarityMatrix(String code, List<String> comments){
        parseCodeString(code);
        parseComments(comments);
        if(LSTM.currentFile != null){
            this.matrix = new Matrix<>(LSTM.m, LSTM.n , new DoubleValue(0));
            List<LSTM.SIM> pairs = LSTM.cases.get(LSTM.currentFile);
            for(LSTM.SIM pair : pairs){
                int row = pair.code;
                int col = pair.comment;
                double sim = pair.sim;
                this.matrix.setValue(row, col, sim);
            }
        }
    }

    private void parseCodeString(String code){
        this.codeString = code;
        CodeLineRelationGraph graph = new CodeLineRelationGraph();
        graph.build(code);
        codeTrees = graph.getCodeLineTrees();
        sliceMatrix = graph.slicesMatrix;
        codeTf = graph.tokenOccurFrequency;

        for(CodeStructureTree codeTree: codeTrees){
            codeTree.generate();
        }

        LSTM.m = codeTrees.size();
    }

    private void parseComments(List<String> comments){

        this.comments = comments;
        textTf = new HashMap<>();
        textTrees = new ArrayList<>();
        for(String comment : comments){
            TextStructureTree textTree = new TextStructureTree(0);
            textTree.construct(new Sentence(comment));
            textTrees.add(textTree);

            List<Node> nodes = textTree.getAllNodes();
            for(Node node : nodes){
                String content = node.getContent();
                textTf.put(content , textTf.getOrDefault(content , 0) + 1 );
            }
        }
    }




    public Matrix<DoubleValue> getMatrix(){
        String[] tokens = "".split(" ");
        String s = "";
        s.length();
        if(matrix != null)
            return matrix;
        else{
            calculateSimilarityMatrix();
            return matrix;
        }

    }


    private void calculateSimilarityMatrix(){

        this.matrix = new Matrix<>(codeTrees.size() , textTrees.size() , new DoubleValue(0));

        for(int i = 0 ; i < textTrees.size() ; i ++){
            TextStructureTree textTree = textTrees.get(i);
            Map<Integer , List<MatchedNode>> alignments = null; //getMatchedNode(textTree);

            for(Integer codeTreeNum : alignments.keySet()){
                Matrix<DoubleValue> matrixTemp = new Matrix<>(codeTrees.get(codeTreeNum).getEndIndex() + 1 , textTree.getEndIndex() + 1, new DoubleValue(0));

                List<MatchedNode> alignedNodes = alignments.get(codeTreeNum);
                for(MatchedNode matchedNode : alignedNodes){

                    double m1=0, n1=0;
                    for(MatchedNode node: matchedNode.codeNode.matchedCodeNodeList){
                        n1 ++;
                        if(node.textTreeID == i)
                            m1 ++;
                    }

                    double m2= 0, n2=0;
                    for(MatchedNode node: matchedNode.textNode.matchedCodeNodeList){
                        n2 ++;
                        if(node.codeTreeID == codeTreeNum)
                            m2 ++;
                    }
                    double sim = 0.5 * matchedNode.similarity * (m1 / n1 + m2 / n2);

                    /*double sim = matchedNode.similarity /
                            (matchedNode.codeNode.matchedCodeNodeList.size() + matchedNode.textNode.matchedCodeNodeList.size());*/


                    //String log = "  " + matchedNode.codeNode.getContent() + " " + matchedNode.textNode.getContent() + " : " + matchedNode.similarity + " " + sim + "\n";
                    //String log = matchedNode.logInfo + "  ---" + (matchedNode.codeNode.matchedCodeNodeList.size() + matchedNode.textNode.matchedCodeNodeList.size());
                    String log = matchedNode.logInfo + "  --- 0.5 * " + matchedNode.similarity + " * ( " + m1 + "/" + n1 + "+" + m2 + "/" + n2 + ")";
                    matrixTemp.setValue(matchedNode.codeNode.getId() , matchedNode.textNode.getId(), sim);
                    matrixTemp.setLogInfo(matchedNode.codeNode.getId() , matchedNode.textNode.getId(), log );

                }


                Map<Pair<Integer , Integer> , Double> matchedNodes = new HashMap<>();
                double sim = matrixTemp.similarity(matchedNodes);
                String log = "";
                if(sim > 0){
                    for(Pair<Integer , Integer> pair : matchedNodes.keySet()){
                        log += "\t" + matrixTemp.getLogInfo(pair.getKey(), pair.getValue()) + "\n";
                    }
                }

                double  featureSim = 0;
                CreateClassFeature feature = new CreateClassFeature();
                if(feature.getFeature(textTree)) {
                    featureSim = feature.match(codeTrees.get(codeTreeNum));
                    if(featureSim > 0){
                        sim += featureSim;
                        log += "  CreateClassFeature:" + featureSim + "\n";
                    }
                }

                KeyWordFeature keyWordFeature = new KeyWordFeature();
                if(keyWordFeature.getFeature(textTree)) {
                    featureSim = keyWordFeature.match(codeTrees.get(codeTreeNum));
                    if(featureSim > 0){
                        sim += featureSim;
                        log += "  KeyWordFeature:" + featureSim + "\n";
                    }

                }

                MethodInvocationFeature methodInvocationFeature = new MethodInvocationFeature();
                if(methodInvocationFeature.getFeature(textTree)) {
                    featureSim = methodInvocationFeature.match(codeTrees.get(codeTreeNum));
                    if(featureSim > 0){
                        sim += featureSim;
                        log += "  MethodInvocationFeature:" + featureSim + "\n";
                    }

                }

                log = " " + ( codeTreeNum + 1 ) + " " + ( i + 1 ) + " : "+ sim + "\n" + log;
                this.matrix.setValue(codeTreeNum , i , sim);
                this.matrix.setLogInfo(codeTreeNum , i , log);
            }
        }

        /*if(LSTM.currentFile != null){
            //this.matrix = new Matrix<>(LSTM.m, LSTM.n , new DoubleValue(0));
            List<LSTM.SIM> pairs = LSTM.cases.get(LSTM.currentFile);
            for(LSTM.SIM pair : pairs){
                int row = pair.code;
                int col = pair.comment;
                double sim = pair.sim + this.matrix.getValue(row, col);
                this.matrix.setValue(row, col, sim);
            }
        }*/
    }


}

package cn.edu.pku.sei.structureAlignment.tree;

import cn.edu.pku.sei.structureAlignment.parser.nlp.Dependency;
import cn.edu.pku.sei.structureAlignment.parser.nlp.NLParser;
import cn.edu.pku.sei.structureAlignment.tree.node.Node;
import cn.edu.pku.sei.structureAlignment.tree.node.NodeType;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.simple.*;
import javafx.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by oliver on 2017/12/23.
 */
public class TextStructureTree extends cn.edu.pku.sei.structureAlignment.tree.Tree<TextStructureTree>{
    private static int id;

    public static void main(String[] args){



        //NLParser parser = new NLParser("I have set rowCacheSize to 1000 (with your higher value, it took way too long)");
        //NLParser parser = new NLParser("I have set rowCacheSize to 1000");

        NLParser parser = new NLParser("Create IndexWriter.");
        edu.stanford.nlp.trees.Tree tree = parser.getNLTree();
        tree.pennPrint();
        TextStructureTree structTree = new TextStructureTree(0);
        structTree.construct(new Sentence(" Add document to Lucene index."));

        structTree.print();
        /*JFrame frame = new JFrame();
        Printer printer = new Printer(structTree.getTree(8));
        printer.setBackground(Color.white);
        frame.add(printer);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 1200);
        frame.setVisible(true);*/


    }

    public TextStructureTree(int id){
        this.id = id;
        children = new ArrayList<TextStructureTree>();
    }

    private TextStructureTree(){
        children = new ArrayList<TextStructureTree>();
    }

    public synchronized TextStructureTree construct(Sentence sentence){
        construct(sentence.parse() , null);

        List<Integer> leafNodeIndexInTextTree = new ArrayList<>();
        List<Node> leafNodes = getAllLeafNodes();

        // the tree's order will be the same as the sentence's tokens' order
        for(Node node : leafNodes){
            leafNodeIndexInTextTree.add(node.id);
        }

        List<Dependency> dependencyList = new ArrayList<>();
        List<SemanticGraphEdge> edges = sentence.dependencyGraph().edgeListSorted();
        for(SemanticGraphEdge edge : edges){
            Dependency dependency = new Dependency(edge);
            int source = edge.getSource().index();
            int target = edge.getTarget().index();
            dependency.target.id = leafNodeIndexInTextTree.get(target - 1);
            dependency.source.id = leafNodeIndexInTextTree.get(source - 1);
            dependencyList.add(dependency);
        }

        updateDependencyInfo(this , dependencyList);
        return this;
    }

    private void updateDependencyInfo(TextStructureTree textTree , List<Dependency> dependencyList){
        int startIndex = textTree.getStartIndex();
        int endIndex = textTree.getEndIndex();

        if(textTree.children.size() == 0) return;

        List<Dependency> subDependencyList = new ArrayList<>();
        for(Dependency dependency : dependencyList){
            int targetIndex = dependency.source.id;
            int sourceIndex = dependency.target.id;

            if(targetIndex >= startIndex && targetIndex <= endIndex &&
                    sourceIndex >= startIndex && sourceIndex <= endIndex){
                subDependencyList.add(dependency);
            }
        }
        textTree.dependencies = subDependencyList;

        for(Tree child : textTree.children){
            updateDependencyInfo((TextStructureTree) child , dependencyList);
        }
    }

    private void construct(edu.stanford.nlp.trees.Tree tree , TextStructureTree parent) {
        this.parent = parent;

        String rootLabel = tree.label().toString();
        NodeType type = getNodeType(rootLabel);

        if(tree.children().length == 0){
            String content = tree.nodeString().trim();
            root = new Node(parent.root.getType() , content , id ++);
            root.setDisplayContent(rootLabel);
            startIndex = endIndex = root.getId();

        }else{
            int r_id = id ++;
            int e_id = 0; // endIndex
            root = new Node(type , "" , r_id);
            root.setDisplayContent(rootLabel);
            for(edu.stanford.nlp.trees.Tree child : tree.children()){
                TextStructureTree temp = new TextStructureTree();
                temp.construct(child , this);
                children.add(temp);
                e_id = temp.getEndIndex();
            }
            String content = combineContentOfChildNode();
            root.setContent(content);
            startIndex = r_id;
            endIndex = e_id;
        }
    }

    public String getContent(){
        return this.root.getContent();
    }

    public String combineContentOfChildNode(){
        String result = "";
        for(cn.edu.pku.sei.structureAlignment.tree.Tree child : this.children){
            result += child.getContent() + "  ";
        }
        return result.trim();
    }

    public static NodeType getNodeType(String text){
        if(text.compareTo("R") == 0){
            return NodeType.TEXT_R ;
        }else if(text.compareTo("S") == 0){
            return NodeType.TEXT_S ;
        }else if(text.compareTo("SBAR") == 0){
            return NodeType.TEXT_SBAR ;
        }else if(text.compareTo("SBARQ") == 0){
            return NodeType.TEXT_SBARQ ;
        }else if(text.compareTo("SINV") == 0){
            return NodeType.TEXT_SINV;
        }else if(text.compareTo("SQ") == 0){
            return NodeType.TEXT_SQ ;
        }else if(text.compareTo("ADJP") == 0){
            return NodeType.TEXT_ADJP ;
        }else if(text.compareTo("CONJP") == 0){
            return NodeType.TEXT_CONJP ;
        }else if(text.compareTo("FRAG") == 0){
            return NodeType.TEXT_FRAG ;
        }else if(text.compareTo("INTJ") == 0){
            return NodeType.TEXT_INTJ ;
        }else if(text.compareTo("LST") == 0){
            return NodeType.TEXT_LST ;
        }else if(text.compareTo("NAC") == 0){
            return NodeType.TEXT_NAC ;
        }else if(text.compareTo("NP") == 0){
            return NodeType.TEXT_NP ;
        }else if(text.compareTo("NX") == 0){
            return NodeType.TEXT_NX ;
        }else if(text.compareTo("PP") == 0){
            return NodeType.TEXT_PP ;
        }else if(text.compareTo("PRN") == 0){
            return NodeType.TEXT_PRN ;
        }else if(text.compareTo("PRT") == 0){
            return NodeType.TEXT_PRT ;
        }else if(text.compareTo("QP") == 0){
            return NodeType.TEXT_QP ;
        }else if(text.compareTo("RRC") == 0){
            return NodeType.TEXT_RRC ;
        }else if(text.compareTo("UCP") == 0){
            return NodeType.TEXT_UCP ;
        }else if(text.compareTo("VP") == 0){
            return NodeType.TEXT_VP ;
        }else if(text.compareTo("WHADJP") == 0){
            return NodeType.TEXT_WHADJP ;
        }else if(text.compareTo("WHAVP") == 0){
            return NodeType.TEXT_WHAVP ;
        }else if(text.compareTo("WHNP") == 0){
            return NodeType.TEXT_WHNP ;
        }else if(text.compareTo("WHPP") == 0){
            return NodeType.TEXT_WHPP ;
        }else if(text.compareTo("X") == 0){
            return NodeType.TEXT_X ;
        }else if(text.compareTo("CD") == 0){
            return NodeType.TEXT_CD ;
        }else if(text.compareTo("DT") == 0){
            return NodeType.TEXT_DT ;
        }else if(text.compareTo("EX") == 0){
            return NodeType.TEXT_EX ;
        }else if(text.compareTo("FW") == 0){
            return NodeType.TEXT_FW ;
        }else if(text.compareTo("IN") == 0){
            return NodeType.TEXT_IN ;
        }else if(text.compareTo("JJ") == 0){
            return NodeType.TEXT_JJ ;
        }else if(text.compareTo("JJR") == 0){
            return NodeType.TEXT_JJR ;
        }else if(text.compareTo("JJS") == 0){
            return NodeType.TEXT_JJS ;
        }else if(text.compareTo("LS") == 0){
            return NodeType.TEXT_LS ;
        }else if(text.compareTo("MD") == 0){
            return NodeType.TEXT_MD ;
        }else if(text.compareTo("NN") == 0){
            return NodeType.TEXT_NN ;
        }else if(text.compareTo("NNS") == 0){
            return NodeType.TEXT_NNS ;
        }else if(text.compareTo("NNP") == 0){
            return NodeType.TEXT_NNP ;
        }else if(text.compareTo("NNPS") == 0){
            return NodeType.TEXT_NNPS ;
        }else if(text.compareTo("PDT") == 0){
            return NodeType.TEXT_PDT ;
        }else if(text.compareTo("POS") == 0){
            return NodeType.TEXT_POS ;
        }else if(text.compareTo("PRP") == 0){
            return NodeType.TEXT_PRP ;
        }else if(text.compareTo("PRP$") == 0){
            return NodeType.TEXT_PRP$ ;
        }else if(text.compareTo("RB") == 0){
            return NodeType.TEXT_RB ;
        }else if(text.compareTo("RBR") == 0){
            return NodeType.TEXT_RBR ;
        }else if(text.compareTo("RBS") == 0){
            return NodeType.TEXT_RBS ;
        }else if(text.compareTo("RP") == 0){
            return NodeType.TEXT_RP ;
        }else if(text.compareTo("SYM") == 0){
            return NodeType.TEXT_SYM ;
        }else if(text.compareTo("TO") == 0){
            return NodeType.TEXT_TO ;
        }else if(text.compareTo("UH") == 0){
            return NodeType.TEXT_UH ;
        }else if(text.compareTo("VB") == 0){
            return NodeType.TEXT_VB ;
        }else if(text.compareTo("VBD") == 0){
            return NodeType.TEXT_VBD ;
        }else if(text.compareTo("VBG") == 0){
            return NodeType.TEXT_VBG ;
        }else if(text.compareTo("VBN") == 0){
            return NodeType.TEXT_VBN ;
        }else if(text.compareTo("VBP") == 0){
            return NodeType.TEXT_VBP ;
        }else if(text.compareTo("VBZ") == 0){
            return NodeType.TEXT_VBZ ;
        }else if(text.compareTo("WDT") == 0){
            return NodeType.TEXT_WDT ;
        }else if(text.compareTo("WP") == 0){
            return NodeType.TEXT_WP ;
        }else if(text.compareTo("WP$") == 0){
            return NodeType.TEXT_WP$ ;
        }else if(text.compareTo("WRB") == 0){
            return NodeType.TEXT_WRB ;
        }else {
            return NodeType.NULL;
        }
    }

    public String getDisplayContent(){
        //return root.getDisplayContent();
        return root.getId() + ": " + root.getDisplayContent();
    }

    public ArrayList<TextStructureTree> findAllVP(){
        ArrayList<TextStructureTree> result = new ArrayList<TextStructureTree>();

        for(Tree child : children){
            ArrayList<TextStructureTree> temp = ((TextStructureTree)child).findAllVP();
            if(temp.size() >0 ){
                result.addAll(temp);
            }
        }
        if(root.getDisplayContent().compareTo("VP") == 0){
            result.add(this);
        }
        return result;
    }

    public ArrayList<TextStructureTree> findAllVerb(){
        ArrayList<TextStructureTree> result = new ArrayList<>();
        String type = root.getDisplayContent();
        if(type.compareTo("VB") == 0 ||
                type.compareTo("VBD") == 0 ||
                type.compareTo("VBG") == 0 ||
                type.compareTo("VBN") == 0 ||
                type.compareTo("VBP") == 0 ||
                type.compareTo("VBZ") == 0 ){
            result.add((TextStructureTree) children.get(0));
        }else if(children.size() > 0){
            int size = children.size();
            for(int i = 0 ; i < size ; i ++){
                TextStructureTree c = (TextStructureTree) children.get(i);
                result.addAll(c.findAllVerb());
            }
        }
        return result;
    }

    public ArrayList<String> findAllNoun(){
        ArrayList<String> result = new ArrayList<>();
        String type = root.getDisplayContent().trim();
        if(type.compareTo("NN") == 0 ||
                type.compareTo("NNS") == 0 ||
                type.compareTo("NNP") == 0 ||
                type.compareTo("NNPS") == 0){
            result.add(children.get(0).root.getContent().trim());
        }else if(children.size() > 0){
            int size = children.size();
            for(int i = 0 ; i < size ; i++){
                TextStructureTree c = (TextStructureTree)children.get(i);
                result.addAll(c.findAllNoun());
            }
        }
        return result;
    }

    public List<Dependency> getDependency(String longName){
        String rootString = root.getContent();
        List<Dependency> result = new ArrayList<>();
        if(dependencies.size() == 0) return null;
        else{
            for(Dependency dependency : dependencies){
                if(dependency.getRelation().compareTo(longName) == 0){
                    result.add(dependency);
                }
            }
        }
        return result;
    }

}

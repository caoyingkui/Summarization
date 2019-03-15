package cn.edu.pku.sei.structureAlignment.tree;

import cn.edu.pku.sei.structureAlignment.tree.node.Node;

/**
 * Created by oliver on 2018/3/22.
 * MatchedNode is used to recode the matched node with the similarity
 */
public class MatchedNode {
    // MatchedNode is stored in the nodes of codeTree or textTree
    // So , it is only needed to store the matched nodes' info
    // for a text node , the matchedTreeID means the matched code tree id , vice versa

    public int codeTreeID;
    public Node codeNode;
    public int textTreeID;
    public Node textNode;
    public double similarity;
    public String logInfo;

    //public int matchedTreeID;
    //public int matchedNodeID;
    //public Node matchedNode;


    public MatchedNode(int codeTreeID, Node codeNode, int textTreeID, Node textNode , double sim){
        this.codeTreeID = codeTreeID;
        this.codeNode = codeNode;
        this.textTreeID = textTreeID;
        this.textNode = textNode;
        this.similarity = sim;
    }


    /*public MatchedNode(int treeId , int NodeId , Node node , double sim){
        matchedTreeID = treeId;
        matchedNodeID = NodeId;
        matchedNode = node;
        similarity = sim;
    }*/
}

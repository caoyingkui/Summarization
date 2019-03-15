package cn.edu.pku.sei.structureAlignment.feature;

import cn.edu.pku.sei.structureAlignment.tree.CodeStructureTree;
import cn.edu.pku.sei.structureAlignment.tree.node.NodeType;
import cn.edu.pku.sei.structureAlignment.tree.TextStructureTree;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by oliver on 2018/3/25.
 */
public class KeyWordFeature extends Feature {
    Map<NodeType, String> typeKeyWordSet;

    public KeyWordFeature(){
        typeKeyWordSet = new HashMap<>();
    }

    @Override
    public double getWeight() {
        return super.getWeight();
    }

    @Override
    public boolean getFeature(TextStructureTree textTree) {
        boolean result = false;
        String content = textTree.getContent().toLowerCase();
        if(content.contains("if")){
            typeKeyWordSet.put( NodeType.CODE_IfStatement , "if");
            result = true;
        }

        if(content.contains("iterate")){
            typeKeyWordSet.put( NodeType.CODE_WhileStatement , "iterate" );
            typeKeyWordSet.put( NodeType.CODE_ForStatement , "iterate");
            result = true;
        }

        return result;
    }

    @Override
    public boolean getFeature(String nlText, Object... arguments) {
        return false;
    }

    @Override
    public double match(CodeStructureTree codeStructureTree) {

        if(typeKeyWordSet.size() > 0){

            for(NodeType type : typeKeyWordSet.keySet()){
                String keyWord = typeKeyWordSet.get(type);
                if(findNodeType(codeStructureTree , type )) {
                    if (keyWord.compareTo("iterate") == 0 )
                        return 1;
                    else if (keyWord.compareTo( "if") == 0)
                        return 1;
                    else
                        return 1;
                }
            }
        }
        return 0;
    }

    private boolean findNodeType(CodeStructureTree codeTree , NodeType nodeType){
        if(codeTree.root.getType() == nodeType)
            return true;

        List<CodeStructureTree> children = codeTree.getChildren();
        if(children.size() > 0) {
            for(CodeStructureTree child : children){
                if(findNodeType(child , nodeType))
                    return true;
            }
            return false;
        }

        return false;
    }
}

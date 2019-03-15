package cn.edu.pku.sei.structureAlignment.feature;

import cn.edu.pku.sei.structureAlignment.database.ApiDB;
import cn.edu.pku.sei.structureAlignment.parser.nlp.Dependency;
import cn.edu.pku.sei.structureAlignment.tree.CodeStructureTree;
import cn.edu.pku.sei.structureAlignment.tree.node.NodeType;
import cn.edu.pku.sei.structureAlignment.tree.TextStructureTree;
import cn.edu.pku.sei.structureAlignment.tree.Tree;
import cn.edu.pku.sei.structureAlignment.util.Stemmer;

import java.util.*;

import static cn.edu.pku.sei.structureAlignment.tree.node.NodeType.CODE_VariableDeclarationStatement;

/**
 * Created by oliver on 2018/3/10.
 */
public class CreateClassFeature extends Feature {

    Set<String> classSet ;
    Set<String> optionalClassSet;

    public CreateClassFeature(){
        classSet = new HashSet<>();
        optionalClassSet = new HashSet<>();
        weight = Feature.defaultWeight;
    }

    public CreateClassFeature(double weight){
        this.weight = weight;
    }

    @Override
    public  boolean getFeature(TextStructureTree textTree){
        boolean result = false;
        List<Dependency> dependencies = textTree.getDependency("direct object");
        if(dependencies != null) {
            for (Dependency dependency : dependencies) {
                ApiDB.conn.setPreparedStatement("select name from api where name = ? and type = 'CLASS'");
                String verb = dependency.getSource().toLowerCase();
                if (Stemmer.equal(verb,"create","get", "instantiate") ){
                    String object = dependency.getTarget();

                    ApiDB.conn.setString(1, object);
                    try {
                        if (ApiDB.conn.executeQuery().next()) {
                            classSet.add(object);
                            result = true;
                        }else{
                            optionalClassSet.add(object);
                            result = true;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        dependencies = textTree.getDependency("nmod_preposition");
        dependencies.addAll(textTree.getDependency("nominal modifier"));
        if(dependencies != null){
            for(Dependency dependency : dependencies){
                String verb = dependency.getSource().toLowerCase();
                if(Stemmer.equal(verb , "create" , "get")){
                    String object = dependency.getTarget();
                    optionalClassSet.add(object);
                    result = true;
                }
            }
        }

        return result;
    }

    @Override
    public boolean getFeature(String nlText, Object... arguments) {
        return false;
    }

    @Override
    public double match(CodeStructureTree codeStructureTree) {

        if(classSet.size() > 0){
            if(findVariableDeclarationStatement(codeStructureTree , classSet))
                return 1;
            else
                return -4;
        }else if(optionalClassSet.size() > 0){
            if(findVariableDeclarationStatement(codeStructureTree , optionalClassSet))
                return 1;
            else
                return 0;
        }

        return 0;
    }

    private boolean findVariableDeclarationStatement(CodeStructureTree codeTree , Set<String> clazzSet){
        if(codeTree.root.getType() == CODE_VariableDeclarationStatement ){
            return findClassName(codeTree , clazzSet);
        }else{
            List<CodeStructureTree> children = codeTree.getChildren();
            if(children.size() > 0){
                for(CodeStructureTree child : children){
                    if(findVariableDeclarationStatement(child , clazzSet))
                        return true;
                }
                return false;
            }else{
                return false;
            }
        }
    }

    private boolean findClassName(CodeStructureTree codeTree , Set<String> clazzSet){

        List<CodeStructureTree> children = codeTree.getChildren();
        if(children.size() == 0){
            if(clazzSet.contains(codeTree.getCode())){
                return true;
            }else{
                return false;
            }
        }else{
            for(CodeStructureTree child : children){
                if(findClassName(child , clazzSet))
                    return true;
            }
            return false;
        }
    }

    private List<CodeStructureTree> findClassInstanceCreationNode(CodeStructureTree tree){
        List<CodeStructureTree> result = new ArrayList<>();
        if(tree.root.getType() == NodeType.CODE_ClassInstanceCreation){
            result.add(tree);
        }else{
            for(Tree child : tree.getChildren()){
                result.addAll(findClassInstanceCreationNode((CodeStructureTree) child));
            }
        }
        return result;
    }

}

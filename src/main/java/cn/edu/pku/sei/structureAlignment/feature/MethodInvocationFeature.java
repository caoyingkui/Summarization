package cn.edu.pku.sei.structureAlignment.feature;

import cn.edu.pku.sei.structureAlignment.alignment.NodeComparator;
import cn.edu.pku.sei.structureAlignment.parser.code.ClassJavadoc;
import cn.edu.pku.sei.structureAlignment.parser.nlp.Dependency;
import cn.edu.pku.sei.structureAlignment.tree.CodeStructureTree;
import cn.edu.pku.sei.structureAlignment.tree.node.Node;
import cn.edu.pku.sei.structureAlignment.tree.node.NodeType;
import cn.edu.pku.sei.structureAlignment.tree.TextStructureTree;
import cn.edu.pku.sei.structureAlignment.util.Stemmer;

import java.util.*;

/**
 * Created by oliver on 2018/1/18.
 */
public class MethodInvocationFeature extends Feature {
    Map<String , Set<String>> verbObjectGroup ;

    public MethodInvocationFeature(){
        verbObjectGroup = new HashMap<>();
    }

    @Override
    public double getWeight() {
        return super.getWeight();
    }

    @Override
    public boolean getFeature(TextStructureTree textTree) {
        boolean result = false;
        List<Dependency> dependencies = textTree.getDependency("direct object");
        if(dependencies.size() > 0 ){
            result = true;
            for(Dependency dependency : dependencies){
                String verb = Stemmer.stemSingleWord(dependency.getSource().toLowerCase());
                String object = Stemmer.stemSingleWord(dependency.getTarget().toLowerCase());

                if(verbObjectGroup.containsKey(verb)){
                    verbObjectGroup.get(verb).add(object);
                }else{
                    Set<String> objects = new HashSet<>();
                    objects.add(object);
                    verbObjectGroup.put(verb , objects);
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
        return findMethodInvocation(codeStructureTree);
    }

    private double findMethodInvocation(CodeStructureTree codeTree){
        double result = 0 ;
        final double completely = 4;
        final double partial = 1;
        List<Node> leafNodes = codeTree.getAllLeafNodes();

        for(Node node : leafNodes){
            NodeType type = node.getType();
            if(type == NodeType.ADDED_METHOD_NAME){
                String methodName = node.getContent();
                List<String> subNames = Stemmer.camelCase(methodName);
                for(String verb : verbObjectGroup.keySet()){
                    if(subNames.contains(verb)) {
                        Set<String> objects = verbObjectGroup.get(verb);
                        if(methodNameContainsObject(subNames, objects)){
                            return completely; // 直接返回
                        }

                        if(containObject(leafNodes , objects))
                            result = partial; //保留一下值，以防万一有返回值为completely的情况。
                    }
                }
            }/*else if(type == NodeType.CODE_SimpleName){
                String className = node.getContent();

                if(!ClassJavadoc.contains(className)){
                    className = "";
                    for(String alternative : node.alternatives){
                        if(ClassJavadoc.contains(alternative)) {
                            className = alternative;
                            break;
                        }
                    }
                }
                if(className.length() == 0)
                    continue;

                for(String verb : verbObjectGroup.keySet()){
                    if(ClassJavadoc.javadocContains(className , verb , verbObjectGroup.get(verb))){
                        return completely;
                    }
                }
            }*/
        }
        return result;


        /*List<CodeStructureTree> children = codeTree.getChildren();
        if(children.size() == 0)
            return 0;
        else{
            if(codeTree.root.getType() == NodeType.CODE_MethodInvocation){
                for(String verb : verbObjectGroup.keySet()){
                    double findVerbResult = findVerb(codeTree , verb);
                    if(findVerbResult > 0)
                        result += findVerbResult * findObjects(codeTree , verbObjectGroup.get(verb));
                }
                return result;
            }else{
                for(CodeStructureTree child : children){
                    result += findMethodInvocation(child);
                }
                return result;
            }
        }*/
    }

    boolean methodNameContainsObject(List<String> subs , Set<String> objects){
        for(String sub : subs){
            for(String object : objects) {
                if (sub.compareTo(object) == 0)
                    return true;
                else if (NodeComparator.isAbbreviation(sub, object))
                    return true;
            }
        }
        return false;
    }

    boolean containObject(List<Node> codeNodes, Set<String> objects){
        for(Node node : codeNodes){
            for(String object : objects){
                if(NodeComparator.contentEqual(node , object))
                    return true;
            }
        }
        return false;
    }

    private double findVerb(CodeStructureTree codeTree , String verb ){
        double result = 0;
        List<CodeStructureTree> children = codeTree.getChildren();
        if(children.size() == 0){
            String content = codeTree.getContent().toLowerCase();
            if(content.compareTo(verb) == 0 ||
                    content.contains(verb)){
                return 1;
            }
        }else{
            for(CodeStructureTree child : children){
                result = findVerb(child , verb);
                if(result > 0)
                    return result;
            }

        }
        return 0;
    }

    private double findObjects(CodeStructureTree codeTree , Set<String> objects){
        double result = 0;
        if(objects.size() == 0)
            return 0;
        List<CodeStructureTree> children = codeTree.getChildren();
        if(children.size() == 0){
            String content = codeTree.getContent().toLowerCase();
            if(objects.contains(Stemmer.stemSingleWord(content))){
                objects.remove(content);
                return 1;
            }else{
                return 0;
            }
        }else{
            for(CodeStructureTree child : children){
                result += findObjects(child , objects);
            }
            return result;
        }
    }
}

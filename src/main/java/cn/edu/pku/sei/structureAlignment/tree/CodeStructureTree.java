package cn.edu.pku.sei.structureAlignment.tree;


import cn.edu.pku.sei.structureAlignment.parser.code.ClassJavadoc;
import cn.edu.pku.sei.structureAlignment.parser.code.CodeVisitor;
import cn.edu.pku.sei.structureAlignment.tree.node.Node;
import cn.edu.pku.sei.structureAlignment.tree.node.NodeType;
import cn.edu.pku.sei.structureAlignment.util.Stemmer;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

/**
 * Created by oliver on 2017/12/23.
 */
public class CodeStructureTree extends Tree<CodeStructureTree>{

    protected String code;
    private Map<String , Object> properties;
    public int startPosition = 0;

    public void setProperty(String propertyName , Object data){
        if(properties == null)
            properties = new HashMap<>();
        properties.put(propertyName , data);
    }

    public Object getProperty(String propertyName){
        if(properties.containsKey(propertyName))
            return properties.get(propertyName);
        else
            return null;
    }

    public static void main(String[] args ){
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        ///parser.setSource("XSSFCellStyle style = new XSSFCellStyle(new StylesTable());".toCharArray());
        parser.setEnvironment(new String[]{""}, new String[]{""}, new String[] { "UTF-8" }, true);
        parser.setSource((
                "indexWriter.AddDocument(document);"
                ).toCharArray());
        //parser.setSource("d = null;".toCharArray());

        //parser.setSource("public class test{}".toCharArray());
        parser.setKind(ASTParser.K_STATEMENTS);

        int[] test = new int[]{1 , 2};
        Block block = (Block)parser.createAST(null);

        CodeVisitor visitor = new CodeVisitor(0);
        ((ASTNode)block.statements().get(0)).accept(visitor);
        //block.accept(visitor);

        CodeStructureTree tree = visitor.getTree();
        tree.print();

    }

    public String getCode() {
        return this.code;
    }

    public CodeStructureTree(Node root , String code , CodeStructureTree parent){
        this.root = root;
        this.code = code;
        this.children = new ArrayList<CodeStructureTree>();
        this.parent = parent;
        startIndex = endIndex = root.getId();

    }

    public void setChildren(List<CodeStructureTree> children){
        int e_id = 0; // endIndex
        for(CodeStructureTree child : children){
            this.children.add(child);
            e_id = child.getEndIndex();
        }

        String content = combineContentOfChildNode();
        this.root.setContent(content);
        endIndex = e_id;
    }

    public String combineContentOfChildNode(){
        String result = "";
        for(Tree child : this.children){
            result += (" " + child.getContent().trim() );
        }
        return result.trim();
    }

    public List<CodeStructureTree> getSpecificTypeNode(NodeType type){
        List<CodeStructureTree> result = new ArrayList<>();

        if(root.type == type){
            result.add(this);
        }

        if(children.size() > 0){
            for(Tree child : children){
                result.addAll(((CodeStructureTree)child).getSpecificTypeNode(type));
            }
        }
        return result;
    }

    //this function only can be used when tree type is MethodInvocation
    public String getMethodInvocationName(){
        if(root.type != NodeType.CODE_MethodInvocation)
            return null;
        else{
            ASTParser parser = ASTParser.newParser(AST.JLS8);
            parser.setSource(code.toCharArray());
            parser.setKind(ASTParser.K_STATEMENTS);
            MethodInvocation methodInvocation = (MethodInvocation)parser.createAST(null);
            return methodInvocation.getName().toString();
        }
    }

    @Override
    public String getContent() {
        String result = "";
        result = root.getContent();
        result += " " + root.getAdditionalInfo();
        return result;
    }


    public String getDisplayContent(){
        String result = "";
        result += root.getId() + ": ";
        if(children.size() == 0) result += root.getDisplayContent();//result += root.getAdditionalInfo() + " " + root.getDisplayContent( ) ;
        else result += root.getType().toString().substring(5);

        return result;
    }


    public Map<String, String> generate(){
        Map<String, String> result = new HashMap<>();
        String methname = "";
        String apiseq = "";
        String tokens = "";

        List<Node> nodes = this.getAllLeafNodes();
        for(Node node : nodes){
            if(node.type == NodeType.ADDED_METHOD_NAME){
                methname += (node.getContent() + " ");
                apiseq += (node.getContent() + " ");
            }

            for(String al : node.alternatives){
                if(ClassJavadoc.contains(al))
                    apiseq += (al + " ");
            }

            tokens +=( node.getContent() + " ");
        }

        String temp = "";
        for(String word : methname.trim().split(" ")){
            if(word.length() == 0)
                continue;
            for(String token :Stemmer.camelCase(word)){
                temp += (token.toLowerCase() + " ");
            }
        }
        result.put("methname", temp.trim());

        result.put("apiseq", apiseq);

        Set<String> tokenSet = new HashSet<>();
        for(String word: tokens.trim().split(" ")){
            if(word.length() == 0) continue;

            for(String token : Stemmer.camelCase(word)){
                tokenSet.add(token.toLowerCase() );
            }
        }
        result.put("tokens" , String.join(" " , tokenSet));

        /*System.out.println("**************************************");
        System.out.println(this.getCode());
        System.out.println("methname: " + result.get("methname"));
        System.out.println("apiseq: " + result.get("apiseq"));
        System.out.println("tokens: " + result.get("tokens") + "\n");*/
       return result;
    }


}

package cn.edu.pku.sei.structureAlignment.util;

import cn.edu.pku.sei.structureAlignment.CodeLineRelation.CodeLineRelationGraph;
import cn.edu.pku.sei.structureAlignment.tree.CodeStructureTree;
import cn.edu.pku.sei.structureAlignment.tree.node.Node;
import cn.edu.pku.sei.structureAlignment.tree.node.NodeType;

import java.util.*;

/**
 * Created by oliver on 2018/3/17.
 */
public class CamelCaseDictionary {
    String baseWord = "";
    public Map<String , CamelCaseDictionary> subWords;


    public static void main(String[] args){
        String code =
                "IndexWriter writer = new IndexWriter(\"MyIndexFolder\", new StandardAnalyzer());\n" +
                        "var doc = new Document();\n" +
                        "var idField = new Field(\"id\", \"MyItemId\", Field.Store.YES, Field.Index.NOT_ANALYZED);\n" +
                        "doc.Add(idField);\n" +
                        "writer.AddDocument(doc);\n" +
                        "writer.Commit();\n" +
                        "Term idTerm = new Term(\"id\", \"MyItemId\");\n" +
                        "writer.DeleteDocuments(idTerm);\n" +
                        "writer.Commit();";

        CodeLineRelationGraph graph = new CodeLineRelationGraph();
        graph.build(code);

        CamelCaseDictionary dictionary = new CamelCaseDictionary(graph);

        String text = dictionary.mergeTokenByCamelCase("You can use IndexWriter.DeleteDocuments(Term), but the tricky part is making sure you've stored your id field correctly in the first place.");
    }

    public CamelCaseDictionary(String baseWord){
        this.baseWord = baseWord;
        subWords = new HashMap<>();
    }

    public CamelCaseDictionary(CodeLineRelationGraph graph){
        subWords = new HashMap<>();

        Map<String , String[]> camelCaseWords = getAllCamelCaseWordsFromCodeSnippet(graph);

        for(String camelCaseWord : camelCaseWords.keySet()){
            String[] splitWords = camelCaseWords.get(camelCaseWord);
            int splitWordLength = splitWords.length;
            CamelCaseDictionary former = this;
            for(int i = 0 ; i < splitWordLength ; i ++){
                String split = splitWords[i];
                if(former.subWords.containsKey(split)){
                    former = former.subWords.get(split);
                }else{
                    CamelCaseDictionary temp = new CamelCaseDictionary(former.baseWord + split);
                    former.subWords.put(split.toLowerCase() , temp);
                    former = temp;
                }
            }
            former.subWords.put("" , null);
        }

    }

    private Map<String , String[]> getAllCamelCaseWordsFromCodeSnippet(CodeLineRelationGraph graph){
        Map<String , String[]> result = new HashMap<>();

        List<CodeStructureTree> codeLines = graph.getCodeLineTrees();
        for(CodeStructureTree codeLine : codeLines){
            List<Node> leafNodes = codeLine.getAllLeafNodes();
            String camelCasePattern = "([^\\p{L}\\d]+)|(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)|(?<=[\\p{L}&&[^\\p{Lu}]])(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}[\\p{L}&&[^\\p{Lu}]])";

            for(Node leafNode : leafNodes){
                String nodeString = leafNode.getContent();
                if(leafNode.getType() == NodeType.CODE_StringLiteral){
                    nodeString = nodeString.substring(1 , nodeString.length() - 1) ;// remove the punctuation ""
                }

                if(!result.containsKey(nodeString)) {
                    String[] splitStrings = nodeString.split(camelCasePattern);
                    if (splitStrings.length > 1) {
                        result.put(nodeString, splitStrings);
                    }
                }
            }
        }
        return result;
    }

    public String mergeTokenByCamelCase(String text){

        String[] tokens = text.split("[ ]+");

        int tokenCount = tokens.length;

        CamelCaseDictionary former;

        int index;
        for(int i = 0 ; i < tokenCount ; i ++){
            index = i;

            String token = tokens[index].toLowerCase();
            former = this;
            while(former.subWords.containsKey(token)){
                former = former.subWords.get(token);
                token = ++ index < tokenCount ? tokens[index].toLowerCase() : "";
            }
            if(former.subWords.containsKey("") && former.baseWord.length() > 0){
                String camelCaseWord = former.baseWord;
                tokens[i] = camelCaseWord;
                for(int j = i + 1 ; j <= index ; j ++){
                    tokens[j] = "";
                }
                i = index;
            }
        }

        return String.join(" " , tokens);
    }

}

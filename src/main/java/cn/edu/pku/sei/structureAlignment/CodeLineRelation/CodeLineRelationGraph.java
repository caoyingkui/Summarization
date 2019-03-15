package cn.edu.pku.sei.structureAlignment.CodeLineRelation;

import cn.edu.pku.sei.structureAlignment.parser.code.CodeVisitor;
import cn.edu.pku.sei.structureAlignment.parser.code.StatementVisitor;
import cn.edu.pku.sei.structureAlignment.parser.nlp.NLParser;
import cn.edu.pku.sei.structureAlignment.tree.*;
import cn.edu.pku.sei.structureAlignment.tree.node.Node;
import cn.edu.pku.sei.structureAlignment.util.DoubleValue;
import cn.edu.pku.sei.structureAlignment.util.Matrix;
import cn.edu.pku.sei.structureAlignment.util.Stemmer;
import javafx.util.Pair;
import org.eclipse.jdt.core.dom.*;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by oliver on 2018/1/14.
 */
public class CodeLineRelationGraph extends JPanel{
    private class CodeLineNode{
        List<Integer> parents;
        int child;
        int depth;

        public CodeLineNode(){
            parents = null;
            child = -1;
            depth = -1;
        }

        public void addParents(int parent){
            if(parents == null) parents = new ArrayList<>();

            parents.add(parent);
        }
        //region <getter>
        public int getChild(){
            return child;
        }

        public int getDepth(){
            return depth;
        }

        //endregion<getter>

        //region <setter>
        public void setChild(int child){
            this.child = child;
        }

        public void setDepth(int depth){
            this.depth = depth;
        }
        //endregion<setter>
    }
    private String code;
    public Map<Integer ,Integer> statementLineIndexes = new HashMap<>();


    public Map<String , String> variableDictionary;
    private List<CodeLineNode> codeLineRelationNodes;
    private List<CodeStructureTree> codeLineTrees;
    public Matrix<DoubleValue> slicesMatrix;

    public Map<String , Integer> tokenOccurFrequency;

    public String getCode(){
        return code;
    }

    public List<CodeStructureTree> getCodeLineTrees(){
        return codeLineTrees;
    }

    public static void main(String[] args){
        CodeLineRelationGraph graph = new CodeLineRelationGraph();
        graph.build(
                "MultiPhraseQuery query = new MultiPhraseQuery();\n" +
                        "query.add(new Term[] { new Term(\"field\", \"quick\"), new Term(\"field\", \"fast\") }); // 1\n" +
                        "query.add(new Term(\"field\", \"fox\")); // 2\n" +
                        "System.out.println(query);\n" +
                        "TopDocs hits = searcher.search(query, 10);\n" +
                        "assertEquals(\"fast fox match\", 1, hits.totalHits);\n" +
                        "query.setSlop(1);\n" +
                        "hits = searcher.search(query, 10);\n" +
                        "assertEquals(\"both match\", 2, hits.totalHits);");
        graph.paint();
        graph.getCodeLineTrees().get(2).print();
    }

    public CodeLineRelationGraph(){
        codeLineRelationNodes = new ArrayList<>();
        codeLineTrees = new ArrayList<>();
        tokenOccurFrequency = new HashMap<>();
    }

    private static List<ASTNode> getStatements(String code){
        ASTParser parser = ASTParser.newParser(AST.JLS8);
        parser.setSource(code.toCharArray());
        parser.setKind(ASTParser.K_STATEMENTS);

        Block block = (Block)parser.createAST(null);

        StatementVisitor statementVisitor = new StatementVisitor(block);
        return statementVisitor.getStatements();
    }

    /**
     * In CodeVisitor, there is a static map variableDictionary. When we parse several lines of code, it will be regard as code snippets,
     * So variableDiction is used to store all the variable which are declared in those lines of code, the variable name will be mapped to the variable type.
     * Now, we want to build a graph, in this graph, each line of code will be regarded as a node
     * and ,as we know, each line of code will contain some variables which are declared in other code lines
     * so, we will build the graph based on this kind of relation , that is ,
     * if  code line A uses some variable which are declared in code line B, then the node A will be the node B's child, and B will be the parent of A.
     * @param code
     */
    public CodeLineRelationGraph build(String code){
        this.code = code;
        build(getStatements(code));
        calculateTokenOccurFrequency();
        return this;
    }

    public void build(Block block){
        this.code = block.toString();

        StatementVisitor statementVisitor = new StatementVisitor(block);
        build(statementVisitor.getStatements());
        calculateTokenOccurFrequency();
    }

    /**
     * In CodeVisitor, there is a static map variableDictionary. When we parse several lines of code, it will be regard as code snippets,
     * So variableDictionary is used to store all the variable which are declared in those lines of code, the variable name will be mapped to the variable type.
     * Now, we want to build a graph, in this graph, each line of code will be regarded as a node
     * and ,as we know, each line of code will contain some variables which are declared in other code lines
     * so, we will build the graph based on this kind of relation , that is ,
     * if  code line A uses some variable which are declared in code line B, then the node A will be the node B's child, and B will be the parent of A.
     *
     */
    private void build(List<ASTNode> statements){
        mapStatementsToCodeLine(statements);

        try{
            CodeVisitor.initialize();

            //after
            int variableCount = 0;
            int lineCount = 0;

            //region <the definition of the relation>
            //when we build the code relation graph, the nodes' corresponding code will satisfy one of the two following condition
            //there are several parameters occurring in this line and
            //1:all the parameters are not declared in other code line
            //       I call these code line as the source of this code snippet
            //2: some of the parameters are declared in other code lines which have occurred before this code line,
            //   this kind of code lines rely on other code lines(in these code lines, declare the parameter or use the parameter)
            //   for example, in code line c2 use parameter b which is declared in code line c1 , so c2 relies on c1
            //   there is still another situation: both c1 and c2 use(attention this word) parameter b, and these is no other code line, between c1 and c2 , use b
            //         I still regard that c2 rely on c1 , because c1 may change some attribute of b.
            //endregion <the definition of the relation>

            Map<String , Integer> variable_declarationLine = new HashMap<>();

            for(ASTNode statement : statements){
                int temp = variableCount;
                CodeVisitor codeVisitor = new CodeVisitor(0);

                statement.accept(codeVisitor);

                variableCount = CodeVisitor.variableDictionary.size();

                // one code line can declare one or 0 variable
                 assert(variableCount - temp < 2);

                // one variable is declared in this code line , add the variable name into the dictionary
                if(variableCount - temp == 1){
                    String variableName = "";
                    for(String name : CodeVisitor.variableDictionary.keySet()){
                        if(!variable_declarationLine.containsKey(name)){
                            variableName = name;
                            break;
                        }
                    }
                    variable_declarationLine.put(variableName , lineCount);
                }

                CodeStructureTree codeTree = codeVisitor.getTree();
                codeTree.startPosition = statement.getStartPosition();
                //codeTree.updateJavadocInfo(); // add javadoc info
                CodeLineNode node = new CodeLineNode();

                //there will be several parameters in this code line,
                //all the code lines in which these parameters are declared will be the parents of this code line
                List<Integer> parents = findParent(codeTree , variable_declarationLine);

                //after build the codeStructureTree, if this code line is variableDeclaration statement,
                //the current line will be contained in list of parent, so need to remove it
                while(parents.contains(lineCount))
                    parents.remove((Object)lineCount);
                int parentTemp;
                if(parents.size() > 0) {
                    Set<Integer> realParents = new HashSet<>();
                    for (int parent : parents) {
                        parentTemp = getYoungestChild(parent);
                        realParents.add(parentTemp);
                    }
                    for(int parent : realParents){
                        codeLineRelationNodes.get(parent).setChild(lineCount);
                        node.addParents(parent);
                    }
                }

                codeLineRelationNodes.add(node);
                codeLineTrees.add(codeTree);
                lineCount ++;
            }

            // store the map  from variable to class
            variableDictionary = new HashMap<>();
            for(String variable : CodeVisitor.variableDictionary.keySet()){
                variableDictionary.put(new String(variable) , new String(CodeVisitor.variableDictionary.get(variable)));
            }

            // calculate the node depth
            for(int i = 0 ; i < codeLineRelationNodes.size() ; i ++){
                if(codeLineRelationNodes.get(i).parents == null){
                    calculateNodeDepths(i , 0);
                }
            }

            programSlice(statements , variable_declarationLine);
        }catch (Exception e){
            System.out.println("error:" + e.getMessage());
            //e.printStackTrace();
        }
    }

    private void mapStatementsToCodeLine(List<ASTNode> statements){
        List<Pair<Integer ,Integer>> linesStart2End = new ArrayList<>();

        int start = 0;
        int end = 0;
        int length = code.length();
        Pattern pattern = Pattern.compile("\\n");
        Matcher matcher = pattern.matcher(code);
        while(matcher.find()){
            end = matcher.start();
            linesStart2End.add(new Pair<>(start , end));
            start = end + 1;
        }
        if(start < length){
            linesStart2End.add(new Pair<>(start , length - 1));
        }

        for(int i = 0 ; i < statements.size() ; i ++){
            start = statements.get(i).getStartPosition();

            for(int j = 0 ; j < linesStart2End.size() ; j ++){
                Pair<Integer , Integer> line = linesStart2End.get(j);
                int line_start = line.getKey();
                int line_end = line.getValue();
                if(start >= line_start && start <= line_end){
                    statementLineIndexes.put(i , j);
                    break;
                }
            }
        }
    }

    public void paint(){
        JFrame frame = new JFrame();
        this.setBackground(Color.white);
        frame.add(this);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 1200);
        frame.setVisible(true);

    }

    @Override
    public void paint(Graphics g){
        super.paint(g);
        Font font = new Font("Verdana" , Font.BOLD , 12);
        g.setFont(font);

        int width = this.getWidth();

        int margin = 90 , levelMargin  = 60 , radius = 15;
        List<Integer> nodesToPaint = new ArrayList<Integer>();
        //int[] nodeDepths = new int[codeLineRelationNodes.size()];
        int[] parentBottoms_x = new int[codeLineRelationNodes.size()];
        int[] parentBottoms_y = new int[codeLineRelationNodes.size()];


        for(int i = 0 ; i < codeLineRelationNodes.size() ; i++){
            if(codeLineRelationNodes.get(i).parents == null) {
                nodesToPaint.add(i);
            }
        }

        int depthToPaint = 0;
        int center_x , center_y = 30;
        while(nodesToPaint.size() != 0){
            Set<Integer> nodesToPaintTemp = new HashSet<>();
            int nodesToPaintCount = nodesToPaint.size();
            center_x = width / 2 - (nodesToPaintCount - 1) * margin / 2;
            for(int nodeId : nodesToPaint){
                CodeLineNode node = codeLineRelationNodes.get(nodeId);
                if(node.getDepth() == depthToPaint){
                    String content = nodeId + "";
                    int contentWidth = g.getFontMetrics().stringWidth(content);
                    g.drawString(content , center_x - contentWidth / 2 , center_y + 5);

                    g.drawOval(center_x - radius , center_y - radius , 2 * radius , 2 * radius);

                    List<Integer> parents = codeLineRelationNodes.get(nodeId).parents;
                    if(parents != null) {
                        for (int parentId : parents) {
                            g.drawLine(parentBottoms_x[parentId], parentBottoms_y[parentId], center_x, center_y - radius);
                        }
                    }

                    parentBottoms_x[nodeId] = center_x ;
                    parentBottoms_y[nodeId] = center_y + radius;
                }else if(node.getDepth() < depthToPaint){
                    List<Integer> parents = codeLineRelationNodes.get(nodeId).parents;
                    g.drawLine(parentBottoms_x[nodeId], parentBottoms_y[nodeId], center_x, center_y + radius);

                    parentBottoms_x[nodeId] = center_x ;
                    parentBottoms_y[nodeId] = center_y + radius;
                }

                int child = codeLineRelationNodes.get(nodeId).child;
                if(child != -1){
                    if(codeLineRelationNodes.get(child).getDepth() == depthToPaint + 1){
                        nodesToPaintTemp.add(child);
                    }else{
                        nodesToPaintTemp.add(nodeId);
                    }
                }
                center_x += margin;
            }



            nodesToPaint.clear();
            nodesToPaint.addAll(nodesToPaintTemp);
            nodesToPaintTemp.clear();
            depthToPaint ++;
            center_y += levelMargin;
        }

    }

    private void calculateNodeDepths(int id , int depth ){
        CodeLineNode node = codeLineRelationNodes.get(id);
        if(node.getDepth() == -1 || node.getDepth() < depth){
            node.setDepth(depth);
        }

        int child = node.child ;
        if(child != -1){
            calculateNodeDepths(child , node.getDepth() + 1);
        }
    }


    /**
     * base on the codeStructureTree of code, find all the parameters, refer to the dictionary
     * if the parameter are declared in code line n, then n will be returned
     * @param codeTree, codeStructureTree of code snippet
     * @param variable_declarationLine  parameter dictionary which maps parameter to the line declares the parameter
     * @return
     */
    public List<Integer> findParent(CodeStructureTree codeTree , Map<String , Integer> variable_declarationLine){
        List<Integer> result = new ArrayList<>();
        List<Node> leafNodes = codeTree.getAllLeafNodes();
        String variable = "";

        for(Node leafNode: leafNodes){
            variable = leafNode.getContent();
            if(variable_declarationLine.containsKey(variable))
                result.add(variable_declarationLine.get(variable));
        }

        return result;
    }

    public int getYoungestChild(int id){
        int result = -1;
        if(codeLineRelationNodes.size() > id) {
            CodeLineNode node = codeLineRelationNodes.get(id);
            result = id;
            while(node.child != -1){
                result = node.child;
                node = codeLineRelationNodes.get(result);
            }
        }
        return result;
    }


    List<CodeStructureTree> getKeyCodeLine(){
        List<CodeStructureTree> result = new ArrayList<>();

        for(int i = 0 ; i < codeLineRelationNodes.size() ; i ++){
            CodeLineNode node = codeLineRelationNodes.get(i);
            if(node.parents!= null && node.parents.size() > 1){
                result.add(codeLineTrees.get(i));
            }
        }

        return result;
    }

    public double compare(String text){
        NLParser nlParser = new NLParser(text);
        String subjectNoun = nlParser.getSubjectiveNoun();
        if(subjectNoun != null)
            subjectNoun = Stemmer.stemSingleWord(subjectNoun);
        else
            subjectNoun = "";

        String verb = nlParser.getVerb();
        if(verb != null)
            verb = Stemmer.stemSingleWord(verb);
        else
            verb = "";

        List<String> nouns = Stemmer.stem(  nlParser.getNonSubjectiveNoun() );

        List<CodeStructureTree> keyCodeLines = getKeyCodeLine();

        Set<String> tokens = new HashSet<>();
        for(CodeStructureTree codeTree: keyCodeLines){
            String code = codeTree.getCode();
            tokens.addAll(Stemmer.stem(code));
        }

        int signal = 0;
        if(tokens.contains(subjectNoun )) signal ++;
        if(tokens.contains(verb) ) signal ++;
        for(String noun : nouns){
            if(tokens.contains(noun)){
                signal ++;
                break;
            }
        }

        if(signal > 0)
            return 1;
        else
            return 0;


    }

    private void programSlice(List<ASTNode> statements , Map<String , Integer> variable_declarationLine){
        int statementsCount = statements.size();
        slicesMatrix = new Matrix<>(statementsCount , statementsCount , new DoubleValue(0));

        //line_variable记录在某行中，一共定义了多少变量名
        Map<Integer , List<String>> line_variable = new HashMap<>();
        for(String variable : variable_declarationLine.keySet()){
            int line = variable_declarationLine.get(variable);
            if(line_variable.containsKey(line)){
                line_variable.get(line).add(variable);
            }else{
                List<String> list = new ArrayList<>();
                list.add(variable);
                line_variable.put(line , list);
            }
        }

        // region <如果第i行中包含了i-1行中定义的变量，那么matrix[i][i-1] = 1 , 表示当第i行对应某句注释时，i-1行如果没有匹配到其他注释，则也把i-1行与之匹配>
        for(int i = 0; i < statementsCount ; i ++){
            slicesMatrix.setValue(i , i , 1);
            int temp = i - 1;
            String code = statements.get(i).toString();
            while(line_variable.containsKey((Object)temp)){
                List<String> variables = line_variable.get((Object)temp);
                boolean obtainsVariableDeclaredInLine_Temp = false;
                for(String variable : variables){
                    if(code.contains(variable)){
                        obtainsVariableDeclaredInLine_Temp = true;
                        break;
                    }
                }
                if(obtainsVariableDeclaredInLine_Temp){
                    slicesMatrix.setValue(i , temp , 1);
                    temp --;
                }else{
                    break;
                }
            }
        }
        // endregion

        for(int i = 0 ; i < statementsCount ; i ++){
            ASTNode statement = statements.get(i);

            // ExpressionStatement 对应的是   qualified.name();   注意是带分号
            //则，ExpressionStatement.getExpression()的结果 才是 ： methodInvocation， 即qualified.name()  注意没有分号

            if(statement instanceof ExpressionStatement && ((ExpressionStatement) statement).getExpression() instanceof MethodInvocation){

                MethodInvocation method = (MethodInvocation) ((ExpressionStatement) statement).getExpression();
                Expression qualifiedExpression = method.getExpression();
                String methodQualifiedName = qualifiedExpression == null ? "" : qualifiedExpression.toString();
                String methodName = method.getName().toString();

                ASTNode formerStatement = i - 1 > -1 ? statements.get(i - 1) : null;
                if(formerStatement instanceof ExpressionStatement && ((ExpressionStatement) formerStatement).getExpression() instanceof MethodInvocation){
                    MethodInvocation formerMethod = (MethodInvocation) ((ExpressionStatement) formerStatement).getExpression();
                    qualifiedExpression = formerMethod.getExpression();
                    String formerMethodQualifiedName = qualifiedExpression == null ? "" : qualifiedExpression.toString();
                    String formerMethodName = formerMethod.getName().toString();

                    if(methodQualifiedName.compareTo("") == 0){
                        if(formerMethodQualifiedName.compareTo("") == 0 &&
                                methodName.compareTo(formerMethodName) == 0){
                            slicesMatrix.setValue(i , i - 1 , 1);
                        }
                    }
                    else if(methodQualifiedName.compareTo(formerMethodQualifiedName) == 0){
                        slicesMatrix.setValue(i , i - 1 , 1);
                    }
                }

                ASTNode latterStatement = i + 1 < statementsCount ? statements.get(i + 1) : null;
                if(latterStatement instanceof ExpressionStatement && ((ExpressionStatement) latterStatement).getExpression() instanceof MethodInvocation){
                    MethodInvocation latterMethod = (MethodInvocation) ((ExpressionStatement) latterStatement).getExpression();
                    qualifiedExpression = latterMethod.getExpression();
                    String latterMethodQualifiedName = qualifiedExpression == null ? "" : qualifiedExpression.toString();
                    String latterMethodName = latterMethod.getName().toString();

                    if(latterMethodQualifiedName.compareTo(methodQualifiedName) == 0 &&
                            latterMethodName.compareTo(methodName) == 0){
                        slicesMatrix.setValue(i , i + 1 , 1);
                    }

                }

            }else if(statement instanceof IfStatement ||
                    statement instanceof WhileStatement ||
                    statement instanceof ForStatement ||
                    statement instanceof TryStatement ||
                    statement instanceof EnhancedForStatement ||
                    statement instanceof DoStatement){

                int statementCount = 0;
                int start = (int) statement.getProperty("start");
                int end = (int) statement.getProperty("end");
                for(int j = start + 1 ; j < end ; ){
                    ASTNode statementTemp = statements.get(j);
                    statementCount ++ ;

                    Object next = statementTemp.getProperty("end");
                    if(next == null)
                        j ++;
                    else
                        j = (int) next + 1;
                }

                if(statementCount < 4) { // 一个这样的切块不能超过两句
                    String blockString = "";
                    for(int j = start ; j < end ; j++){
                        blockString += statements.get(j).toString() + "\n";
                        slicesMatrix.setValue(j , j + 1 , 1);
                        slicesMatrix.setValue(j + 1 , j , 1);
                    }

                    for(int j = start - 1; j > -1 ; j --){
                        if(line_variable.containsKey(j)){
                            boolean signal = false;
                            for(String variable : line_variable.get(j)){
                                if(blockString.contains(variable)){
                                    signal = true;
                                    slicesMatrix.setValue(j , j + 1 , 1);
                                    slicesMatrix.setValue(j + 1 , j , 1);
                                    break;
                                }
                            }
                            if(!signal)
                                break;
                        }else {
                            break;
                        }
                    }
                }
            }
        }

        return;
    }

    private void calculateTokenOccurFrequency(){
        for(CodeStructureTree codeTree : codeLineTrees){
            List<Node> leafNodes = codeTree.getAllLeafNodes();
            for(Node leafNode : leafNodes){
                String content = leafNode.getContent();

                if(tokenOccurFrequency.containsKey(content)){
                    tokenOccurFrequency.put(content , tokenOccurFrequency.get(content) + 1);
                }else{
                    tokenOccurFrequency.put(content , 1);
                }
            }
        }
    }

}

package cn.edu.pku.sei.structureAlignment.parser.code;

import cn.edu.pku.sei.structureAlignment.tree.CodeStructureTree;
import cn.edu.pku.sei.structureAlignment.tree.node.Node;
import cn.edu.pku.sei.structureAlignment.tree.node.NodeType;
import cn.edu.pku.sei.structureAlignment.util.DigitsToWord;
import javafx.util.Pair;
import mySql.SqlConnector;
import org.eclipse.jdt.core.dom.*;


import java.io.*;
import java.sql.ResultSet;
import java.util.*;


/**
 * Created by oliver on 2017/12/24.
 */
public class CodeVisitor extends ASTVisitor {
    private CodeStructureTree tree;
    private List<CodeStructureTree> children;
    private static int id ;
    public static Map<String , String> variableDictionary ;
    private CodeStructureTree parent ; // this is used to set the parent of the tree

    private static SqlConnector conn; // this is for connecting the javadoc database
    private static String tableName;

    static{
        ResourceBundle bundle = ResourceBundle.getBundle("database");
        String url = bundle.getString("luceneAPI_url");
        String user = bundle.getString("db_user");
        String pwd = bundle.getString("db_pwd");
        String driver = bundle.getString("db_driver");
        tableName = bundle.getString("luceneAPI_table");

        conn = new SqlConnector(url , user , pwd , driver);
        conn.start();

        initialize();
    }

    //this constructor only be used when we need to construct a new codeStructTree
    public CodeVisitor(int id){
        this.id = id;
        children = new ArrayList<CodeStructureTree>();
        this.parent = null;
    }

    //this constructor only be used inside codeVisitor ,
    //it means when we try to construct the sub-part of a tree, we use this constructor to build the sub-tree
    // usually , the top node a tree will be encoded with 0 ,
    // and there is a static id, which is used to store the number which will be used for next node.
    private CodeVisitor(CodeStructureTree parent){
        children = new ArrayList<CodeStructureTree>();
        this.parent = parent;
    }

    public CodeStructureTree getTree(){
        return this.tree;
    }

    private List<CodeStructureTree> batchProcess(List<ASTNode> nodes , String separator , NodeType separatorType , CodeStructureTree parentTree){
        List<CodeStructureTree> result = new ArrayList<CodeStructureTree>();

        CodeStructureTree separatorTree = null;

        for(ASTNode node : nodes){
            if(separatorTree != null){
                result.add(separatorTree);
                id ++;
            }

            CodeVisitor nodeVisitor = new CodeVisitor(parentTree);
            node.accept(nodeVisitor);
            result.add(nodeVisitor.getTree());

            Node separatorRoot = new Node(separatorType , separator , id);
            separatorTree = new CodeStructureTree(separatorRoot , separator , parentTree);
        }

        return result;
    }

    public static void initialize(){
        variableDictionary = new HashMap<>();
        id = 0;
    }

    public static String getVariableType (String variable){
        return variableDictionary.getOrDefault(variable , "");
    }

    @Override
    public boolean visit(AnnotationTypeDeclaration node) {
        return false;
    }

    @Override
    public boolean visit(AnnotationTypeMemberDeclaration node) {
        return false;
    }

    @Override
    public boolean visit(AnonymousClassDeclaration node) {
        return false;
    }

    @Override
    public boolean visit(ArrayAccess node) {
        //grammar: Expression '[' Expression ']'

        //region <construct the tree of root>
        Node root = new Node(NodeType.CODE_ArrayAccess , "" , id ++);
        this.tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of root>

        //region <construct the tree of the first Expression>
        Expression arrayExpression = node.getArray();
        CodeVisitor arrayVisitor = new CodeVisitor(this.tree);
        arrayExpression.accept(arrayVisitor);
        children.add(arrayVisitor.getTree());
        //endregion <construct the tree of the first Expression>

        //region <construct the tree of [ >
        Node lbRoot = new Node(NodeType.ADDED_CHAR_LEFT_BRACKET , "[" , id ++);
        CodeStructureTree lbTree = new CodeStructureTree(lbRoot , "[" , this.tree);
        children.add(lbTree);
        //endregion <construct the tree of [>


        //region <construct the tree of the index>
        Expression indexExpression = node.getIndex();
        CodeVisitor indexVisitor = new CodeVisitor(tree);
        indexExpression.accept(indexVisitor);
        CodeStructureTree indexTree = indexVisitor.getTree();
        children.add(indexTree);
        //endregion <construct the tree of index>

        //region <construct the tree of ] >
        Node rbRoot = new Node(NodeType.ADDED_CHAR_LEFT_BRACKET , "]" , id ++);
        CodeStructureTree rbTree = new CodeStructureTree(rbRoot , "]" , tree);
        children.add(rbTree);
        //endregion <construct the tree of ]>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(ArrayCreation node) {
        // region <grammar>
        /**
         * new PrimitiveType '[' Expression ']' { '[' Expression ']' } { '[' ']' }
         *
         * new TypeName [ < Type { , Type } > ] '[' Expression ']' { '[' Expression ']' } { '[' ']' }
         *
         * new PrimitiveType '[' ']' { '[' ']' } ArrayInitializer
         *
         * new TypeName [ < Type { , Type } > ] '[' ']' { '[' ']' } ArrayInitializer
         */
        // endregion <grammar>

        /** note
         * for example , the node represents the code "new int[6][5][]"
         * node.getType() will return  "int [][]" , and use the return node n , call the function n.getElementType() will return int
         *                                          and use the return node n , call the function n.dimensions().size will return 3
         * however , the node.dimensions() will return list of expressions ,  in this expression ,will return {6 ,5}
         *
         */


        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_ArrayCreation , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of new>
        Node newRoot =new Node(NodeType.ADDED_KEYWORD , "new" , id ++);
        CodeStructureTree newTree = new CodeStructureTree(newRoot , "new" , tree);
        children.add(newTree);
        //endregion <construct the tree of new>

        //region <construct the tree of type and dimensions>
        ASTNode type = node.getType().getElementType();
        int dimensionLength = node.getType().dimensions().size();
        List<ASTNode> dimensionWidths = node.dimensions();


            //region <construct the tree of type>
        CodeVisitor typeVisitor = new CodeVisitor(tree);
        type.accept(typeVisitor);
        children.add(typeVisitor.getTree());
            //endregion <construct the tree of type>

        int d = 0;
        for( ; d < dimensionWidths.size() ; d++){
            //region <construct the tree of [ >
            Node lbRoot = new Node(NodeType.ADDED_CHAR_LEFT_BRACKET , "[" , id ++);
            CodeStructureTree lbTree = new CodeStructureTree(lbRoot , "[" , tree);
            children.add(lbTree);
            //endregion <construct the tree of [ >

            //region <construct the tree of dimension>
            ASTNode dimension = dimensionWidths.get(d);
            CodeVisitor dimensionVisitor = new CodeVisitor(tree);
            dimension.accept(dimensionVisitor);
            children.add(dimensionVisitor.getTree());
            //endregion <construct the tree of dimension>

            //region <construct the tree of ] >
            Node rbRoot = new Node(NodeType.ADDED_CHAR_RIGHT_BRACKET , "]" , id ++);
            CodeStructureTree rbTree = new CodeStructureTree(rbRoot , "]" , tree);
            children.add(rbTree);
            //endregion <construct the tree of ] >
        }
        while(d < dimensionLength){
            //region <construct the tree of [ >
            Node lbRoot = new Node(NodeType.ADDED_CHAR_LEFT_BRACKET , "[" , id ++);
            CodeStructureTree lbTree = new CodeStructureTree(lbRoot , "[" , tree);
            children.add(lbTree);
            //endregion <construct the tree of [ >

            //region <construct the tree of ] >
            Node rbRoot = new Node(NodeType.ADDED_CHAR_RIGHT_BRACKET , "]" , id ++);
            CodeStructureTree rbTree = new CodeStructureTree(rbRoot , "]" , tree);
            children.add(rbTree);
            //endregion <construct the tree of ] >

            d ++;
        }


        //endregion <construct the tree of type and dimensions>

        //region <construct the tree of ArrayInitializer>
        ASTNode initializer = node.getInitializer();
        if(initializer != null) {
            CodeVisitor initializerVisitor = new CodeVisitor(tree);
            initializer.accept(initializerVisitor);
            children.add(initializerVisitor.getTree());
        }
        //endregion <construct the tree of ArrayInitializer>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(ArrayInitializer node) {
        //region <grammar>
        /**
         * '{'[ Expression { , Expression} [ , ]] '}'
         */
        //endregion <grammar>

        //region <construct the tree of root>
        Node root = new Node(NodeType.CODE_ArrayInitializer , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent );
        //endregion <construct the tree of root>

        //region <construct the tree of {>""
        Node lbRoot = new Node(NodeType.ADDED_CHAR_LEFT_BRACE , "{" , id ++);
        CodeStructureTree lbTree = new CodeStructureTree(lbRoot , "{" , tree);
        children.add(lbTree);
        //endregion <construct the tree of {>

        //region <construct the tree of expressions>
        List<ASTNode> expressions = node.expressions();
        List<CodeStructureTree> expressionTrees = batchProcess(expressions , "," , NodeType.ADDED_CHAR_COMMA , tree);
        children.addAll(expressionTrees);
        //endregion <construct the tree of expressions>

        //region <construct the tree of }>""
        Node rbRoot = new Node(NodeType.ADDED_CHAR_LEFT_BRACE , "}" , id ++);
        CodeStructureTree rbTree = new CodeStructureTree(rbRoot , "}" , tree);
        children.add(rbTree);
        //endregion <construct the tree of }>


        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(ArrayType node) {
        //region <grammar>
        /**
         * Type Dimension {Dimension}
         */
        //endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_ArrayType , "" , id ++);
        this.tree = new CodeStructureTree(root , node.toString() , parent);
        children = new ArrayList<CodeStructureTree>();
        //endregion <construct the tree of the root>

        //region <construct the tree of the Type>
        Type type = node.getElementType();
        CodeVisitor typeVisitor = new CodeVisitor(tree);
        type.accept(typeVisitor);
        children.add(typeVisitor.getTree());
        //endregion <construct the tree of the Type>

        //region <construct the tree of Dimension>
        //TODO
        //endregion <construct the tree of Dimension>

        this.tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(AssertStatement node) {
        // region <grammar>
        /**
         * assert Expression [ : Expression ] ;
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_AssertStatement , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of first expression>
        Expression first_expression = node.getExpression();
        CodeVisitor fExpressionVisitor = new CodeVisitor(tree);
        first_expression.accept(fExpressionVisitor);
        children.add(fExpressionVisitor.getTree());
        //endregion <construct the tree of first expression>

        //region <construct the tree of second expression>
        Expression second_expression = node.getMessage();
        if(second_expression != null){
            //region <construct the tree of semicolon>
            Node semicolonRoot = new Node(NodeType.ADDED_CHAR_SEMICOLON , ":" , id ++);
            CodeStructureTree semicolonTree = new CodeStructureTree(semicolonRoot , ":" , tree);
            children.add(semicolonTree);
            //endregion <construct the tree of semicolon>

            //region <construct the tree of second expression>
            CodeVisitor sExpressionVisitor = new CodeVisitor(tree);
            second_expression.accept(sExpressionVisitor);
            children.add(sExpressionVisitor.getTree());
            //endregion <construct the tree of second expression>
        }
        //endregion <construct the tree of second expression>

        //region <construct the tree of comma>
        Node commaRoot = new Node(NodeType.ADDED_CHAR_COMMA , ";" , id ++);
        CodeStructureTree commaTree = new CodeStructureTree(commaRoot , ";" , tree);
        children.add(commaTree);
        //endregion <construct the tree of comma>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(Assignment node) {
        // region <grammar>
        /**
         * Expression AssignmentOperator Expression
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_Assignment , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of L_expression>
        Expression l_expression = node.getLeftHandSide();
        CodeVisitor l_expressionVisitor = new CodeVisitor(tree);
        l_expression.accept(l_expressionVisitor);
        children.add(l_expressionVisitor.getTree());
        //endregion <construct the tree of L_expression>

        //region <construct  the tree of operator>
        Assignment.Operator operator = node.getOperator();
        Node operatorRoot = new Node(NodeType.CODE_ASSIGNMENT_OPERATOR , operator.toString() , id ++) ;
        CodeStructureTree operatorTree = new CodeStructureTree(operatorRoot , operator.toString() ,tree);
        children.add(operatorTree);
        //endregion <construct  the tree of operator>

        //region <construct the tree of R_expression>
        Expression r_expression = node.getRightHandSide();
        CodeVisitor r_expressionVisitor = new CodeVisitor(tree);
        r_expression.accept(r_expressionVisitor);
        children.add(r_expressionVisitor.getTree());
        //endregion <construct the tree of L_expression>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(Block node) {
        // region <grammar>
        /**
         * { { Statement } }
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_Block , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of {>
        Node lbRoot = new Node(NodeType.ADDED_CHAR_LEFT_BRACE , "{" , id ++);
        CodeStructureTree lbTree = new CodeStructureTree(lbRoot , "{" , tree);
        children.add(lbTree);
        //endregion <construct the tree of }>

        //region <construct the tree of Statement>
        List<Statement> statements = node.statements();
        if(statements != null){
            for(Statement statement : statements){
                CodeVisitor statementVisitor = new CodeVisitor(tree);
                statement.accept(statementVisitor);
                children.add(statementVisitor.getTree());
            }
        }
        //endregion <construct the tree of Statement>

        //region <construct the tree of >
        Node rbRoot = new Node(NodeType.ADDED_CHAR_RIGHT_BRACE , "}" , id ++);
        CodeStructureTree rbTree = new CodeStructureTree(rbRoot , "}" , tree);
        children.add(rbTree);
        //endregion <construct the tree of>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(BlockComment node) {
        return false;
    }

    @Override
    public boolean visit(BooleanLiteral node) {
        // region <grammar>
        /**
         * true
         * false
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_BooleanLiteral , node.toString() , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent );
        //endregion <construct the tree of the root>

        return false;
    }

    @Override
    public boolean visit(BreakStatement node) {
        // region <grammar>
        /**
         * break [ Identifier ] ;
         */
        // endregion <grammar>

        //region <construct the tree of root>
        Node root = new Node(NodeType.CODE_BreakStatement , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of root>

        //region <construct the tree of the root>
        CodeStructureTree breakTree = buildKeyWordTree("break" , tree);
        children.add(breakTree);
        //endregion <construct the tree of the root>

        //region <construct the tree of identifier>
        ASTNode identifier = node.getLabel();
        if(identifier != null) {
            CodeVisitor identifierVisitor = new CodeVisitor(tree);
            identifier.accept(identifierVisitor);
            children.add(identifierVisitor.getTree());
        }
        //endregion <construct the tree of identifier>

        //region <construct the tree of comma>
        CodeStructureTree commaTree = buildPunctuationTree(";" , NodeType.ADDED_CHAR_COMMA , tree);
        children.add(commaTree);
        //endregion <construct the tree of comma>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(CastExpression node) {
        // region <grammar>
        /**
         * (Type) Expression
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_CastExpression , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of ( >
        CodeStructureTree lpTree = buildPunctuationTree("(" , NodeType.ADDED_CHAR_LEFT_PARENTHESIS , tree);
        children.add(lpTree);
        //endregion <construct the tree of ( >

        //region <construct the tree of Type>
        Type type = node.getType();
        CodeVisitor typeVisitor = new CodeVisitor(tree);
        type.accept(typeVisitor);
        children.add(typeVisitor.getTree());
        //endregion <construct the tree of Type>

        //region <construct the tree of ) >
        CodeStructureTree rpTree = buildPunctuationTree(")" , NodeType.ADDED_CHAR_RIGHT_PARENTHESIS , tree);
        children.add(rpTree);
        //endregion <construct the tree of ) >

        //region <construct the tree of Expression>
        Expression expression = node.getExpression();
        CodeVisitor expressionVisitor = new CodeVisitor(tree);
        expression.accept(expressionVisitor);
        children.add(expressionVisitor.getTree());
        //endregion <construct the tree of Expression>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(CatchClause node) {
        // region <grammar>
        /**
         * catch ( FormalParameter ) Block
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_CatchClause , "" , id ++);
        tree = new CodeStructureTree(root , "" , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of catch>
        CodeStructureTree catchTree = buildKeyWordTree("catch" , tree);
        children.add(catchTree);
        //endregion <construct the tree of catch>

        //region <construct the tree of ( >
        CodeStructureTree lpTree = buildPunctuationTree("(" , NodeType.ADDED_CHAR_LEFT_PARENTHESIS , tree);
        children.add(lpTree);
        //endregion <construct the tree of (>

        //region <construct the tree of FormalParameter>
        SingleVariableDeclaration declaration = node.getException();
        CodeVisitor declarationVisitor = new CodeVisitor(tree);
        declaration.accept(declarationVisitor);
        children.add(declarationVisitor.getTree());
        //endregion <construct the tree of FormalParameter>

        //region <construct the tree of ) >
        CodeStructureTree rpTree = buildPunctuationTree(")" , NodeType.ADDED_CHAR_RIGHT_PARENTHESIS , tree);
        children.add(rpTree);
        //endregion <construct the tree of ) >

        //region <construct the tree of Block>
        Block block = node.getBody();
        CodeVisitor blockVisitor = new CodeVisitor(tree);
        block.accept(blockVisitor);
        children.add(blockVisitor.getTree());
        //endregion <construct the tree of Block>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(CharacterLiteral node) {
        // region <grammar>
        /**
         *
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_CharacterLiteral , node.toString() , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        return false;
    }

    @Override
    public boolean visit(ClassInstanceCreation node) {
        // region <grammar>
        /**
         * [ Expression . ] new [ < Type { , Type } > ] Type ( [ Expression { , Expression } ] ) [ AnonymousClassDeclaration ]
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_ClassInstanceCreation , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of Expression>
        Expression expression = node.getExpression();
        if(expression != null){
            CodeVisitor expressionVisitor = new CodeVisitor(tree);
            expression.accept(expressionVisitor);
            children.add(expressionVisitor.getTree());

            Node dotRoot = new Node(NodeType.ADDED_CHAR_DOT , "." , id ++ );
            CodeStructureTree dotTree = new CodeStructureTree(dotRoot , "." , tree);
            children.add(dotTree);
        }
        //endregion <construct the tree of Expression>

        //region <construct the tree of new>
        Node newRoot = new Node(NodeType.ADDED_KEYWORD , "new" , id ++);
        CodeStructureTree newTree = new CodeStructureTree(newRoot , "new" , tree);
        children.add(newTree);
        //endregion <construct the tree of new>

        //region <construct the tree of Type>
        Type type = node.getType();
        CodeVisitor typeVisitor = new CodeVisitor(tree);
        type.accept(typeVisitor);
        children.add(typeVisitor.getTree());
        //endregion <construct the tree of Type>

        //region <construct the tree of (>
        Node lpRoot = new Node(NodeType.ADDED_CHAR_LEFT_PARENTHESIS , "(" , id ++);
        CodeStructureTree lpTree = new CodeStructureTree(lpRoot , "(" , tree);
        children.add(lpTree);
        //endregion <construct the tree of (>

        //region <construct the tree of arguments>
        List<Expression> arguments = node.arguments();
        CodeStructureTree commaTree = null;
        for(Expression argument : arguments){
            if(commaTree != null){
                children.add(commaTree);
                id ++;
            }
            CodeVisitor argumentVisitor = new CodeVisitor(tree);
            argument.accept(argumentVisitor);
            children.add(argumentVisitor.getTree());

            Node commaRoot = new Node(NodeType.ADDED_CHAR_COMMA , "," , id ); // id 不用自增1
            commaTree = new CodeStructureTree(commaRoot , "," , tree);
        }
        //endregion <construct the tree of arguments>

        //region <construct the tree of )>
        Node rpRoot = new Node(NodeType.ADDED_CHAR_RIGHT_PARENTHESIS , ")" , id ++);
        CodeStructureTree rpTree = new CodeStructureTree(rpRoot , ")" , tree);
        children.add(rpTree);
        //endregion <construct the tree of )>


        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(CompilationUnit node) {
        // region <grammar>
        /**
         * [ PackageDeclaration ]
         *      { ImportDeclaration }
         *      { TypeDeclaration | EnumDeclaration | AnnotationTypeDeclaration | ; }
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_CompilationUnit , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent );
        //endregion <construct the tree of the root>

        //region <construct the tree of PackageDeclaration>
        PackageDeclaration pDeclaration = node.getPackage();
        CodeVisitor pVisitor = new CodeVisitor(tree);
        pDeclaration.accept(pVisitor);
        children.add(pVisitor.getTree());
        //endregion <construct the tree of PackageDeclaration>

        //region <construct the tree of ImportDeclaration>
        List<ImportDeclaration> iDeclarations = node.imports();
        if(iDeclarations != null && iDeclarations.size() > 0){
            for(ImportDeclaration importDeclaration : iDeclarations){
                CodeVisitor iVisitor = new CodeVisitor(tree);
                importDeclaration.accept(iVisitor);
                children.add(iVisitor.getTree());
            }
        }
        //endregion <construct the tree of ImportDeclaration>

        //region <construct the tree of declarations>
        List<ASTNode> typeDeclarations = node.types();
        for(ASTNode typeDeclaration : typeDeclarations){
            CodeVisitor typeVisitor = new CodeVisitor(tree);
            typeDeclaration.accept(typeVisitor);
            children.add(typeVisitor.getTree());
        }
        //endregion <construct the tree of declarations>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(ConditionalExpression node) {
        // region <grammar>
        /**
         * Expression ? Expression : Expression
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_ConditionalExpression , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of conditionExpression>
        Expression condition = node.getExpression();
        CodeVisitor conditionVisitor = new CodeVisitor(tree);
        condition.accept(conditionVisitor);
        children.add(conditionVisitor.getTree());
        //endregion <construct the tree of conditionExpression>

        //region <construct the tree of ?>
        CodeStructureTree questionTree = buildPunctuationTree("?" , NodeType.ADDED_CHAR_QUESTION, tree);
        children.add(questionTree);
        //endregion <construct the tree of ?>

        //region <construct the tree of thenExpression>
        Expression thenExpression = node.getThenExpression();
        CodeVisitor thenVisitor = new CodeVisitor(tree);
        thenExpression.accept(thenVisitor);
        children.add(thenVisitor.getTree());
        //endregion <construct the tree of thenExpression>

        //region <construct the tree of >
        CodeStructureTree commaTree = buildPunctuationTree(":" , NodeType.ADDED_CHAR_COLON , tree );
        children.add(commaTree);
        //endregion <construct the tree of>

        //region <construct the tree of elseExpression>
        Expression elseExpression = node.getThenExpression();
        CodeVisitor elseVisitor = new CodeVisitor(tree);
        elseExpression.accept(elseVisitor);
        children.add(elseVisitor.getTree());
        //endregion <construct the tree of elseExpression>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(ConstructorInvocation node) {
        // region <grammar>
        /**
         *  ConstructorInvocation:
         *      [ < Type { , Type } > ]
         *          this ( [ Expression { , Expression } ] ) ;
         */
        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_ConstructorInvocation , "" , id ++);
        tree = new CodeStructureTree(root , node.toString(), parent);
        // endregion <construct the tree of root>

        // region <construct the tree of Types>
        List<ASTNode> types = node.typeArguments();
        if(types != null && types.size() > 0){
            // region <construct the tree of < >
            CodeStructureTree labTree = buildPunctuationTree("<" , NodeType.ADDED_CHAR_LEFT_ANGLE_BRACKET , tree);

            // endregion <construct the tree of < >

            // region <construct the trees of types>
            List<CodeStructureTree> typeTrees = batchProcess(types , "," , NodeType.ADDED_CHAR_COMMA , tree);
            // endregion <construct the trees of types>

            // region <construct the tree of > >
            CodeStructureTree rabTree = buildPunctuationTree(">" , NodeType.ADDED_CHAR_RIGHT_ANGLE_BRACKET , tree);
            // endregion <construct the tree of > >

            children.add(labTree);
            children.addAll(typeTrees);
            children.add(rabTree);
        }
        // endregion <construct the tree of Types>

        // region <construct the tree of this>
        CodeStructureTree thisTree = buildKeyWordTree("this" , tree);
        children.add(thisTree);
        // endregion <construct the tree of this>

        // region <construct the tree of ( >
        CodeStructureTree lpTree = buildPunctuationTree("(" , NodeType.ADDED_CHAR_LEFT_PARENTHESIS , tree);
        children.add(lpTree);
        // endregion <construct the tree of ( >

        // region <construct the trees of arguments>
        List<ASTNode> expressions = node.arguments();
        if(expressions != null && expressions.size() > 0){
            List<CodeStructureTree> expressionTrees = batchProcess(expressions , "," , NodeType.ADDED_CHAR_COMMA , tree);
            children.addAll(expressionTrees);
        }
        // endregion <construct the trees of arguments>

        // region <construct the tree of )>
        CodeStructureTree rpTree = buildPunctuationTree(")" , NodeType.ADDED_CHAR_RIGHT_PARENTHESIS , tree);
        children.add(rpTree);
        // endregion <construct the tree of ) >

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(ContinueStatement node) {
        // region <grammar>
        /**
         * continue [ Identifier ] ;
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_ContinueStatement , "" , id++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of continue>
        CodeStructureTree continueTree = buildKeyWordTree( "continue" , tree);
        children.add(continueTree);
        //endregion <construct the tree of continue>

        //region <construct the tree of identifier>
        SimpleName identifier = node.getLabel();
        if(identifier != null){
            CodeVisitor identifierVisitor = new CodeVisitor(tree);
            identifier.accept(identifierVisitor);
            children.add(identifierVisitor.getTree());
        }
        //endregion <construct the tree of identifier>

        //region <construct the tree of ;>
        CodeStructureTree semicolonTree = buildPunctuationTree(";" , NodeType.ADDED_CHAR_SEMICOLON , tree);
        children.add(semicolonTree);
        //endregion <construct the tree of ;>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(CreationReference node) {
        // region <grammar>
        /**
         *  CreationReference:
         *      Type :: [ < Type { , Type } > ] new
         */
        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_CreationReference , "" , id++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        // endregion <construct the tree of root>

        // region <construct the tree of Type>
        Type type = node.getType();
        CodeVisitor typeVisitor = new CodeVisitor(tree);
        type.accept(typeVisitor);
        children.add(typeVisitor.getTree());
        // endregion <construct the tree of Type>

        // region <construct the tree of ::>
        CodeStructureTree doubleColonTree = buildPunctuationTree("::" , NodeType.ADDED_CHAR_DOUBLE_COLON , tree);
        children.add(doubleColonTree);
        // endregion <construct the tree of ::>

        // region <construct the trees of type arguments>
        List<ASTNode> types = node.typeArguments();
        if(types != null && types.size() > 0){
            // region <construct the tree of <  >
            CodeStructureTree labTree = buildPunctuationTree("<" , NodeType.ADDED_CHAR_LEFT_ANGLE_BRACKET , tree);
            children.add(labTree);
            // endregion <construct the tree of <  >

            // region <construct the tree of types>
            children.addAll(batchProcess(types , "," , NodeType.ADDED_CHAR_COMMA , tree ));
            // endregion <construct the tree of types>

            // region <construct the tree of >  >
            CodeStructureTree rabTree = buildPunctuationTree(">" , NodeType.ADDED_CHAR_RIGHT_ANGLE_BRACKET , tree);
            children.add(rabTree);
            // endregion <construct the tree of >  >
        }
        // endregion <construct the trees of type arguments>

        // region <construct the tree of new>
        CodeStructureTree newTree = buildKeyWordTree("new" , tree);
        children.add(newTree);
        // endregion <construct the tree of new>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(Dimension node) {
        //region <grammar>
        /**
         * { Annotation } []
         */
        //endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_Dimension , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion

        //region <construct the tree of the Annotation>
        //TODO
        //endregion <construct the tree of the Annotation>

        //region <construct the tree of  [ >
        Node lbRoot = new Node(NodeType.ADDED_CHAR_LEFT_BRACKET , "[" , id ++);
        CodeStructureTree lbTree = new CodeStructureTree(lbRoot , "[" , tree);
        children.add(lbTree);
        //endregion <construct the tree of  [ >

        //region <construct the tree of  ] >
        Node rbRoot = new Node(NodeType.ADDED_CHAR_RIGHT_BRACKET , "]" , id ++);
        CodeStructureTree rbTree = new CodeStructureTree(rbRoot , "]" , tree);
        children.add(rbTree);
        //endregion <construct the tree of  ] >

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(DoStatement node) {
        // region <grammar>
        /**
         *  DoStatement:
         *      do Statement while ( Expression ) ;
         * */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_DoStatement ,"" , id++);
        String code = "do while (" + node.getExpression().toString() +")";
        tree = new CodeStructureTree(root , code , parent);
        tree.setProperty("size" , node.getProperty("size"));
        tree.setProperty("start" , node.getProperty("start"));
        tree.setProperty("end" , node.getProperty("end"));
        //endregion <construct the tree of the root>

        //region <construct the tree of do>
        CodeStructureTree doTree = buildKeyWordTree("do" , tree);
        children.add(doTree);
        //endregion <construct the tree of do>

        //region <construct the tree of statement>
        /*
        Statement statement = node.getBody();
        CodeVisitor statementVisitor = new CodeVisitor(tree);
        statement.accept(statementVisitor);
        children.add(statementVisitor.getTree());
        */
        //endregion <construct the tree of statement>

        //region <construct the tree of while>
        CodeStructureTree whileTree = buildKeyWordTree("while" , tree);
        children.add(whileTree);
        //endregion <construct the tree of while>

        //region <construct the tree of  (>
        CodeStructureTree lpTree = buildPunctuationTree("(" , NodeType.ADDED_CHAR_LEFT_PARENTHESIS , tree);
        children.add(lpTree);
        //endregion <construct the tree of (>

        //region <construct the tree of Expression>
        Expression condition = node.getExpression();
        CodeVisitor conditionVisitor = new CodeVisitor(tree);
        condition.accept(conditionVisitor);
        children.add(conditionVisitor.getTree());
        //endregion <construct the tree of Expression>

        //region <construct the tree of )>
        CodeStructureTree rpTree = buildPunctuationTree(")" , NodeType.ADDED_CHAR_RIGHT_PARENTHESIS , tree);
        children.add(rpTree);
        //endregion <construct the tree of )>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(EmptyStatement node) {
        // region <grammar>
        /**
         * ;
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_EmptyStatement , ";" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>
        return false;
    }

    @Override
    public boolean visit(EnhancedForStatement node) {
        // region <grammar>
        /**
         * for ( FormalParameter : Expression ) Statement
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_EnhancedForStatement , "" , id++);
        String code = "for(" + node.getParameter().toString() + " : "+ node.getExpression().toString() + ")";
        tree = new CodeStructureTree(root , code , parent);
        tree.setProperty("size" , node.getProperty("size"));
        tree.setProperty("start" , node.getProperty("start"));
        tree.setProperty("end" , node.getProperty("end"));
        //endregion <construct the tree of the root>

        //region <construct the tree of (>
        CodeStructureTree lpTree = buildPunctuationTree("(" , NodeType.ADDED_CHAR_LEFT_PARENTHESIS , tree);
        children.add(lpTree);
        //endregion <construct the tree of (>

        //region <construct the tree of formalParameter>
        SingleVariableDeclaration parameter = node.getParameter();
        CodeVisitor parameterVisitor = new CodeVisitor(tree);
        parameter.accept(parameterVisitor);
        children.add(parameterVisitor.getTree());
        //endregion <construct the tree of formalParameter>

        //region <construct the tree of :>
        CodeStructureTree colonTree = buildPunctuationTree(":" , NodeType.ADDED_CHAR_COLON , tree);
        children.add(colonTree);
        //endregion <construct the tree of :>


        //region <construct the tree of Expression>
        Expression expression = node.getExpression();
        CodeVisitor expressionVisitor = new CodeVisitor(tree);
        expression.accept(expressionVisitor);
        children.add(expressionVisitor.getTree());
        //endregion <construct the tree of Expression>

        //region <construct the tree of )>
        CodeStructureTree rpTree = buildPunctuationTree(")" , NodeType.ADDED_CHAR_RIGHT_PARENTHESIS , tree);
        children.add(rpTree);
        //endregion <construct the tree of )>

        //region <construct the tree of statement>
        /*
        Statement body = node.getBody();
        CodeVisitor bodyVisitor = new CodeVisitor(tree);
        body.accept(bodyVisitor);
        children.add(bodyVisitor.getTree());
        */
        //endregion <construct the tree of statement>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(EnumConstantDeclaration node) {
        return false;
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        return false;
    }

    @Override
    public boolean visit(ExpressionMethodReference node) {
        return false;
    }

    @Override
    public boolean visit(ExpressionStatement node) {
        //grammar: StatementExpression ;

        Node root = new Node(NodeType.CODE_ExpressionStatement , "" , id ++);
        this.tree = new CodeStructureTree(root , node.toString() , parent);
        children = new ArrayList<CodeStructureTree>();

        //construct the tree of StatementExpression
        Expression statement = node.getExpression();
        CodeVisitor statementVisitor = new CodeVisitor(tree);
        statement.accept(statementVisitor);
        CodeStructureTree statementTree = statementVisitor.getTree();
        children.add(statementTree);

        CodeStructureTree commaTree = buildPunctuationTree(";" , NodeType.ADDED_CHAR_COMMA , tree);
        children.add(commaTree);

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(FieldAccess node) {
        // region <grammar>
        /**
         * Expression . Identifier
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_FieldAccess , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of Expression>
        Expression expression = node.getExpression();
        CodeVisitor expressionVisitor = new CodeVisitor(tree);
        expression.accept(expressionVisitor);
        children.add(expressionVisitor.getTree());
        //endregion <construct the tree of Expression>

        //region <construct the tree of .>
        CodeStructureTree dotTree = buildPunctuationTree("." , NodeType.ADDED_CHAR_DOT , tree);
        children.add(dotTree);
        //endregion <construct the tree of .>

        //region <construct the tree of identifier>
        SimpleName identifier = node.getName();
        CodeVisitor identifierVisitor = new CodeVisitor(tree);
        identifier.accept(identifierVisitor);
        children.add(identifierVisitor.getTree());
        //endregion <construct the tree of identifier>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        return false;
    }

    @Override
    public boolean visit(ForStatement node) {
        // region <grammar>
        /**
         * for ( [ ForInit ] ; [ Expression ] ; [ ForUpdate ] )
         *     Statement
         *
         * ForInit:
         *      Expression { , Expression}
         *
         * ForUpdate:
         *      Expression { , Expression}
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_ForStatement , "" , id ++);
        StringBuilder code = new StringBuilder("for(" );
        for(Object n : node.initializers()){
            code.append( n.toString() ).append(" , ");
        }
        code.delete(code.length() - 3 , code.length());
        code.append(" ; ").append(node.getExpression().toString()).append( " ; ");
        for(Object n : node.updaters()){
            code.append(n.toString()).append(" , ") ;
        }
        code.delete(code.length() - 3 , code.length());
        code.append(")");
        tree = new CodeStructureTree(root , code.toString() , parent);
        tree.setProperty("size" , node.getProperty("size"));
        tree.setProperty("start" , node.getProperty("start"));
        tree.setProperty("end" , node.getProperty("end"));
        //endregion <construct the tree of the root>

        //region <construct the tree of (>
        CodeStructureTree lpTree = buildPunctuationTree("(" , NodeType.ADDED_CHAR_LEFT_PARENTHESIS , tree);
        children.add(lpTree);
        //endregion <construct the tree of (>

        //region <construct the tree of ForInit>
        List<ASTNode> initializers = node.initializers();
        if(initializers != null && initializers.size() > 0) {
            List<CodeStructureTree> initializerTrees = batchProcess(initializers, ",", NodeType.ADDED_CHAR_COMMA, tree);
            children.addAll(initializerTrees);
        }
        //endregion <construct the tree of ForInit>

        //region <construct the tree of ;>
        CodeStructureTree semiColonTree = buildPunctuationTree(";" , NodeType.ADDED_CHAR_SEMICOLON , tree);
        children.add(semiColonTree);
        //endregion <construct the tree of ;>

        //region <construct the tree of Expression>
        Expression expression = node.getExpression();
        CodeVisitor expressionVisitor = new CodeVisitor(tree);
        expression.accept(expressionVisitor);
        children.add(expressionVisitor.getTree());
        //endregion <construct the tree of Expression>

        //region <construct the tree of ;>
        semiColonTree = buildPunctuationTree(";" , NodeType.ADDED_CHAR_SEMICOLON , tree);
        children.add(semiColonTree);
        //endregion <construct the tree of ;>

        //region <construct the tree of ForUpdate>
        List<ASTNode> updaters = node.updaters();
        if(updaters != null && updaters.size() > 0) {
            List<CodeStructureTree> updaterTrees = batchProcess(updaters, ",", NodeType.ADDED_CHAR_COMMA, tree);
            children.addAll(updaterTrees);
        }
        //endregion <construct the tree of ForUpdate>

        //region <construct the tree of )>
        CodeStructureTree rpTree = buildPunctuationTree(")" , NodeType.ADDED_CHAR_RIGHT_PARENTHESIS, tree);
        children.add(rpTree);
        //endregion <construct the tree of )>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(IfStatement node) {
        // region <grammar>
        /**
         * if ( Expression ) Statement [ else Statement]
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_IfStatement , "" , id ++);
        String code = "if(" + node.getExpression().toString() + ")";
        tree = new CodeStructureTree(root , code , parent);
        tree.setProperty("size" , node.getProperty("size"));
        tree.setProperty("start" , node.getProperty("start"));
        tree.setProperty("end" , node.getProperty("end"));
        //endregion <construct the tree of the root>

        //region <construct the tree of if>
        CodeStructureTree ifTree = buildKeyWordTree("if" , tree);
        children.add(ifTree);
        //endregion <construct the tree of if>

        //region <construct the tree of (>
        CodeStructureTree lpTree = buildPunctuationTree("(" , NodeType.ADDED_CHAR_LEFT_PARENTHESIS , tree);
        children.add(lpTree);
        //endregion <construct the tree of (>

        //region <construct the tree of Expression>
        Expression expression = node.getExpression();
        CodeVisitor expressionVisitor = new CodeVisitor(tree);
        expression.accept(expressionVisitor);
        children.add(expressionVisitor.getTree());
        //endregion <construct the tree of Expression>

        //region <construct the tree of )>
        CodeStructureTree rpTree = buildPunctuationTree(")" , NodeType.ADDED_CHAR_RIGHT_PARENTHESIS , tree);
        children.add(rpTree);
        //endregion <construct the tree of )>


        //region <construct the tree of Statement>
        /*
        Statement thenStatement = node.getThenStatement();
        CodeVisitor thenStatementVisitor = new CodeVisitor(tree);
        thenStatement.accept(thenStatementVisitor);
        children.add(thenStatementVisitor.getTree());
        */
        //endregion <construct the tree of Statement>

        //region <construct the tree of elseStatement>
        /*
        Statement elseStatement = node.getElseStatement();
        if(elseStatement!= null){
            //region <construct the tree of else>
            CodeStructureTree elseTree = buildKeyWordTree("else" , tree);
            children.add(elseTree);
            //endregion <construct the tree of else>

            CodeVisitor elseStatementVisitor = new CodeVisitor(tree);
            elseStatement.accept(elseStatementVisitor);
            children.add(elseStatementVisitor.getTree());
        }
        */
        //endregion <construct the tree of elseStatement>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(ImportDeclaration node) {
        return false;
    }

    @Override
    public boolean visit(InfixExpression node) {
        // region <grammar>
        /**
         * Expression InfixOperator Expression { InfixOperator Expression }
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_InfixExpression , "" , id ++);
        tree = new CodeStructureTree( root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of >
        List<ASTNode> operands = new ArrayList<>();
        operands.add(node.getLeftOperand());
        operands.add(node.getRightOperand());

        List<Expression> extendedOperands = node.extendedOperands();
        if(extendedOperands != null && extendedOperands.size() > 0)
            operands.addAll(extendedOperands);

        String operator = node.getOperator().toString().trim();
        NodeType operatorType = null;
        //region <get the operator type>
        if(operator.compareTo("+") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_TIMES;
        }else if(operator.compareTo("/") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_DIVIDE;
        }else if(operator.compareTo("%") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_REMAINDER;
        }else if(operator.compareTo("+") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_PLUS;
        }else if(operator.compareTo("-") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_MINUS;
        }else if(operator.compareTo("<<") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_LEFT_SHIFT;
        }else if(operator.compareTo(">>") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_RIGHT_SHIFT;
        }else if(operator.compareTo(">>>") == 0){
            operatorType = NodeType.CODE_ASSIGNMENT_OPERATOR_RIGHT_SHIFT_UNSIGNED_ASSIGN;
        }else if(operator.compareTo("<") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_LESS;
        }else if(operator.compareTo(">") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_GREATER;
        }else if(operator.compareTo("<=") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_LESS_EQUALS;
        }else if(operator.compareTo(">=") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_GREATER_EQUALS;
        }else if(operator.compareTo("==") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_EQUALS;
        }else if(operator.compareTo("!=") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_NOT_EQUALS;
        }else if(operator.compareTo("^") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_XOR;
        }else if(operator.compareTo("&") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_AND;
        }else if(operator.compareTo("|") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_OR;
        }else if(operator.compareTo("&&") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_AND;
        }else if(operator.compareTo("||") == 0){
            operatorType = NodeType.CODE_InfixExpression_OPERATOR_OR;
        }
        //endregion <get the operator type>


        children.addAll(batchProcess(operands , operator , operatorType , tree));
        //endregion <construct the tree of>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(Initializer node) {
        return false;
    }

    @Override
    public boolean visit(InstanceofExpression node) {
        // region <grammar>
        /**
         * Expression instanceof Type
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_InstanceofExpression , "" , id ++);
        tree = new CodeStructureTree(root , "" , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of Expression>
        Expression expression = node.getLeftOperand();
        CodeVisitor expressionVisitor = new CodeVisitor(tree);
        expression.accept(expressionVisitor);
        children.add(expressionVisitor.getTree());
        //endregion <construct the tree of Expression>

        //region <construct the tree of instanceof>
        CodeStructureTree instanceofTree = buildKeyWordTree("instanceof" , tree);
        children.add(instanceofTree);
        //endregion <construct the tree of instanceof>

        //region <construct the tree of Type>
        Type type = node.getRightOperand();
        CodeVisitor typeVisitor = new CodeVisitor(tree);
        type.accept(typeVisitor);
        children.add(typeVisitor.getTree());
        //endregion <construct the tree of Type>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(IntersectionType node) {
        // region <grammar>
        /**
         * Type & Type { & Type }
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_IntersectionType , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of Types>
        List<ASTNode> types = node.types();
        children.addAll( batchProcess(types , "&" , NodeType.CODE_InfixExpression_OPERATOR_AND , tree));
        //region <construct the tree of Types>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(Javadoc node) {
        // region <grammar>
        //     /**  { TagElement } */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_Javadoc , node.toString() , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>


        return false;
    }

    @Override
    public boolean visit(LabeledStatement node) {
        // region <grammar>
        /**
         * Identifier: Statement
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_LabeledStatement , "" , id ++);
        tree = new CodeStructureTree(root , "" , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of Identifier>
        SimpleName identifier = node.getLabel();
        CodeVisitor identifierVisitor = new CodeVisitor(tree);
        identifier.accept(identifierVisitor);
        children.add(identifierVisitor.getTree());
        //endregion <construct the tree of Identifier>

        //region <construct the tree of :>
        CodeStructureTree colonTree = buildPunctuationTree(":" , NodeType.ADDED_CHAR_COLON , tree);
        children.add(colonTree);
        //endregion <construct the tree of :>

        //region <construct the tree of >
        Statement statement = node.getBody();
        CodeVisitor statementVisitor = new CodeVisitor(tree);
        statement.accept(statementVisitor);
        children.add(statementVisitor.getTree());
        //endregion <construct the tree of>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(LambdaExpression node) {
        return false;
    }

    @Override
    public boolean visit(LineComment node) {
        return false;
    }

    @Override
    public boolean visit(MarkerAnnotation node) {
        // region <grammar>
        /**
         *  MarkerAnnotation:
         *      @ TypeName
         */
        // endregion <grammar>


        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_MarkerAnnotation , "" , id ++);
        tree = new CodeStructureTree(root , "" , parent);
        // endregion <construct the tree of root>

        // region <construct the tree of @>
        children.add(
                buildPunctuationTree("@" , NodeType.ADDED_CHAR_AT , tree)
        );
        // endregion <construct the tree of @>

        // region <construct the tree of TypeName>
        Name typeName = node.getTypeName();
        CodeVisitor typeNameVisitor = new CodeVisitor(tree);
        typeName.accept(typeNameVisitor);
        children.add(typeNameVisitor.getTree());
        // endregion <construct the tree of TypeName>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(MemberRef node) {
        return false;
    }

    @Override
    public boolean visit(MemberValuePair node) {
        // region <grammar>
        /**
         *  MemberValuePair:
         *      SimpleName = Expression
         */
        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_MemberValuePair , "" , id++);
        tree = new CodeStructureTree(root , "" , parent);
        // endregion <construct the tree of root>

        // region <construct the tree of SimpleName>
        SimpleName simpleName = node.getName();
        CodeVisitor simpleNameVisitor = new CodeVisitor(tree);
        simpleName.accept(simpleNameVisitor);
        children.add(simpleNameVisitor.getTree());
        // endregion <construct the tree of SimpleName>

        // region <construct the tree of = >
        children.add(
                buildPunctuationTree("=" , NodeType.CODE_InfixExpression_OPERATOR_EQUALS , tree)
        );
        // endregion <construct the tree of = >

        // region <construct the tree of Expression>
        Expression expression = node.getValue();
        CodeVisitor expressionVisitor = new CodeVisitor(tree);
        expression.accept(expressionVisitor);
        children.add(expressionVisitor.getTree());
        // endregion <construct the tree of Expression>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(MethodRef node) {
        return false;
    }

    @Override
    public boolean visit(MethodRefParameter node) {
        // region <grammar>
        /**
         *  MethodRefParameter:
         *      Type [ ... ] [ Identifier ]
         */
        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_MethodRefParameter , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        // endregion <construct the tree of root>

        // region <construct the tree of Type>
        Type type = node.getType();
        CodeVisitor typeVisitor = new CodeVisitor(tree);
        type.accept(typeVisitor);
        children.add(typeVisitor.getTree());
        // endregion <construct the tree of Type>

        // region <construct the tree of ...>
        if(node.isVarargs()){
            children.add(
                    buildPunctuationTree("..." , NodeType.ADDED_CHAR_ELLIPSIS , tree)
            );
        }
        // endregion <construct the tree of ...>

        // region <construct the tree of Identifier>
        SimpleName identifier = node.getName();
        if(identifier != null){
            CodeVisitor identifierVisitor = new CodeVisitor(tree);
            identifier.accept(identifierVisitor);
            children.add(identifierVisitor.getTree());
        }
        // endregion <construct the tree of Identifier>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(MethodDeclaration node) {

        // region <grammar>
        /**
         * MethodDeclaration:
         * [ Javadoc ] { ExtendedModifier } [ < TypeParameter { , TypeParameter } > ] ( Type | void )
         * Identifier (
         * [ ReceiverParameter , ] [ FormalParameter { , FormalParameter } ]
         * ) { Dimension }
         * [ throws Type { , Type } ]
         * ( Block | ; )
         *
         * ConstructorDeclaration:
         * [ Javadoc ] { ExtendedModifier } [ < TypeParameter { , TypeParameter } > ]
         * Identifier (
         * [ ReceiverParameter , ] [ FormalParameter { , FormalParameter } ]
         * ) { Dimension }
         * [ throws Type { , Type } ]
         * ( Block | ; )

         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_MethodDeclaration , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of Javadoc>
        Javadoc javadoc = node.getJavadoc();
        CodeVisitor javadocVisitor = new CodeVisitor(tree);
        javadoc.accept(javadocVisitor);
        children.add(javadocVisitor.getTree());
        //endregion <construct the tree of Javadoc>

        //region <construct the tree of ExtendedModifier>
        List<Modifier> modifiers = node.modifiers();
        if(modifiers != null && modifiers.size() > 0){
            for(Modifier modifier : modifiers){
                CodeStructureTree t =buildKeyWordTree(modifier.getKeyword().toString() , tree);
                children.add(t);
            }
        }
        //endregion <construct the tree of ExtendedModifier>

        //region <construct the tree of TypeParameter>

            //region <construct the tree of < >
        CodeStructureTree lab = buildPunctuationTree("<" , NodeType.ADDED_CHAR_LEFT_ANGLE_BRACKET , tree);
        children.add(lab);
            //endregion <construct the tree of <>

        List<ASTNode> types = node.typeParameters();
        children.addAll( batchProcess(types , "," , NodeType.ADDED_CHAR_COMMA , tree));

            //region <construct the tree of > >
        CodeStructureTree rab = buildPunctuationTree(">" , NodeType.ADDED_CHAR_RIGHT_ANGLE_BRACKET , tree);
        children.add(rab);
            //endregion <construct the tree of > >

        //endregion <construct the tree of TypeParameter>

        //region <construct the tree of returnType>
        if(!node.isConstructor()) {
            Type type = node.getReturnType2();
            if(type != null) {
                CodeVisitor typeVisitor = new CodeVisitor(tree);
                type.accept(typeVisitor);
                children.add(typeVisitor.getTree());
            }else{
                CodeStructureTree voidTree = buildKeyWordTree("void" , tree);
                children.add(voidTree);
            }
        }
        //endregion <construct the tree of returnType>

        //region <construct the tree of Identifier>
        String methodName = node.getName().toString();
        Node methodNameRoot = new Node(NodeType.CODE_SimpleName , methodName , id++);
        CodeStructureTree methodNameTree = new CodeStructureTree(methodNameRoot , methodName , tree);
        children.add(methodNameTree);
        //endregion <construct the tree of Identifier>

        //region <construct the tree of Parameters>
        CodeStructureTree lpTree = buildPunctuationTree("(" , NodeType.ADDED_CHAR_LEFT_PARENTHESIS , tree);
        children.add(lpTree);

        Type receiverType = node.getReceiverType();
        if(receiverType != null){
            CodeVisitor receiverTypeVisitor = new CodeVisitor(tree);
            receiverType.accept(receiverTypeVisitor);
            children.add(receiverTypeVisitor.getTree());

            CodeStructureTree commaTree = buildPunctuationTree("," , NodeType.ADDED_CHAR_COMMA , tree);
            children.add(commaTree);
        }

        List<ASTNode> formalParameters = node.parameters();
        if(formalParameters != null && formalParameters.size() > 0){
            children.addAll( batchProcess(formalParameters , "," , NodeType.ADDED_CHAR_COMMA , tree));
        }

        CodeStructureTree rpTree = buildPunctuationTree(")" , NodeType.ADDED_CHAR_RIGHT_PARENTHESIS , tree);
        children.add(rpTree);
        //endregion <construct the tree of Parameters>

        //region <construct the tree of Dimension>
        List<Dimension> dimensions = node.extraDimensions();
        if(dimensions != null && dimensions.size() >  0){
            for(Dimension dimension : dimensions){
                CodeVisitor dimensionVisitor = new CodeVisitor(tree);
                dimension.accept(dimensionVisitor);
                children.add(dimensionVisitor.getTree());
            }
        }
        //endregion <construct the tree of Dimension>

        //region <construct the tree of Exceptions>
        List<ASTNode> exceptions = node.thrownExceptionTypes();
        if(exceptions != null && exceptions.size() >0){
            CodeStructureTree throwsTree = buildKeyWordTree("throws" , tree);
            children.add(throwsTree);

            children.addAll( batchProcess( exceptions , "," , NodeType.ADDED_CHAR_COMMA , tree));
        }
        //endregion <construct the tree of Exceptions>

        //region <construct the tree of >
        Block body = node.getBody();
        if(body != null){
            CodeVisitor bodyVisitor = new CodeVisitor(tree);
            body.accept(bodyVisitor);
            children.add(bodyVisitor.getTree());
        }else{
            CodeStructureTree semicolonTree = buildPunctuationTree(";" , NodeType.ADDED_CHAR_SEMICOLON , tree);
            children.add(semicolonTree);
        }
        //endregion <construct the tree of>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(MethodInvocation node) {
        //region <grammar>
        /** original
         *  [ Expression . ] [ < Type { , Type } > ] Identifier ( [ Expression { , Expression } ] ) ;
         */
        /** modify as
         *
         * if Expression exists:
         *    [ Expression . ] [ < Type { , Type } > ] MethodInvocation
         *
         * else:
         *    just as the original one
         *
         */

        //endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_MethodInvocation , "" , id ++);
        this.tree = new CodeStructureTree(root , node.toString() , parent);
        children = new ArrayList<CodeStructureTree>();
        //endregion <construct the tree of the root>

        //region <construct the tree of the Expression>
        Expression expression = node.getExpression();
        if(expression != null){
            CodeVisitor expressionVisitor = new CodeVisitor(tree);
            expression.accept(expressionVisitor);
            CodeStructureTree expressionTree = expressionVisitor.getTree();
            children.add(expressionTree);


            Node dotRoot = new Node(NodeType.ADDED_CHAR_DOT , "." , id ++);
            CodeStructureTree dotTree = new CodeStructureTree(dotRoot , "." , tree);
            children.add(dotTree);
        }
        //endregion

        //region <construct the tree of the types>
        List<ASTNode> types = node.typeArguments();
        if(types != null && types.size() > 0){
            //region <construct the tree of <  >
            Node labRoot = new Node(NodeType.ADDED_CHAR_LEFT_ANGLE_BRACKET , "<" , id ++);
            CodeStructureTree labTree = new CodeStructureTree(labRoot , "<" , tree);
            children.add(labTree);
            //endregion <construct the tree of < >

            List<CodeStructureTree> typeTrees = batchProcess(types , "," , NodeType.ADDED_CHAR_COMMA , tree);
            children.addAll(typeTrees);

            //region <construct the tree of > >
            Node rabRoot = new Node(NodeType.ADDED_CHAR_RIGHT_ANGLE_BRACKET , ">" , id ++);
            CodeStructureTree rabTree = new CodeStructureTree(rabRoot , ">" , tree);
            children.add(rabTree);
            //endregion <construct the tree of > >
        }
        //endregion <construct the tree of the types>

        CodeStructureTree optionalInvocationTree = null;
        List<CodeStructureTree> optionalChildren ;

        if(expression != null){
            String code = node.toString().replace(expression.toString() + "." , "");

            Node optionalInvocationRoot = new Node(NodeType.CODE_MethodInvocation , "" , id ++);
            optionalInvocationTree = new CodeStructureTree(optionalInvocationRoot , code , tree);
        }


        //region <construct the tree of the Identifier>
        SimpleName name = node.getName();
        Node nameRoot = new Node(NodeType.ADDED_METHOD_NAME , name.toString() , id ++);
        CodeStructureTree nameTree = new CodeStructureTree(nameRoot , name.toString() , null);
        Map<String , String> methodInfo = getMostPossibleMethodInfo(node);
        if(methodInfo != null){
            nameTree.root.setAdditionalInfo(methodInfo.get("javadoc"));
        }
        //endregion <construct the tree of the Identifier>

        //region <construct the tree of ( >
        Node lpRoot = new Node(NodeType.ADDED_CHAR_LEFT_PARENTHESIS, "(", id++);
        CodeStructureTree lpTree = new CodeStructureTree(lpRoot, "(" , tree);
        //endregion <construct the tree of ( >

        //region <construct the tree of the Arguments>
        List<ASTNode> arguments = node.arguments();
        List<CodeStructureTree> argumentTrees = new ArrayList<CodeStructureTree>();
        if (arguments != null) {
            int argumentCount = arguments.size();
            String[] argumentTypes = null;
            String[] argumentNames = null;
            if(methodInfo != null){
                argumentTypes = methodInfo.get("argumentTypes").split(" \\| ");
                argumentNames = methodInfo.get("argumentNames").split(" \\| ");
            }

            argumentTrees= batchProcess(arguments, ",", NodeType.ADDED_CHAR_COMMA , null);

            if(argumentNames != null){
                for(int i = 0 ; i < argumentCount ; i ++ ){
                    String additionInfo = argumentTypes[i] + " " + argumentNames[i];
                    argumentTrees.get(i * 2).root.setAdditionalInfo(additionInfo);
                }
            }
        }
        //endregion <construct the tree of the Arguments>

        //region <construct the tree of ) >
        Node rpRoot = new Node(NodeType.ADDED_CHAR_RIGHT_PARENTHESIS, ")", id++);
        CodeStructureTree rpTree = new CodeStructureTree(rpRoot, ")" , tree);
        //endregion <construct the tree of ) >


        if(expression != null){
            optionalChildren = new ArrayList<CodeStructureTree>();

            nameTree.setParent(optionalInvocationTree);
            optionalChildren.add(nameTree);

            lpTree.setParent(optionalInvocationTree);
            optionalChildren.add(lpTree);


            for(CodeStructureTree argumentTree : argumentTrees){
                argumentTree.setParent(optionalInvocationTree);
                optionalChildren.add(argumentTree);
            }

            rpTree.setParent(optionalInvocationTree);
            optionalChildren.add(rpTree);

            optionalInvocationTree.setChildren(optionalChildren);
            children.add(optionalInvocationTree);
        }else{

            nameTree.setParent(tree);
            children.add(nameTree);

            lpTree.setParent(tree);
            children.add(lpTree);

            for(CodeStructureTree argumentTree : argumentTrees){
                argumentTree.setParent(tree);
                children.add(argumentTree);
            }

            rpTree.setParent(tree);
            children.add(rpTree);
        }

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(Modifier node) {
        // region <grammar>
        /**
         * public | protected | private | static | abstract | final | native | synchronized | transient | volatile | strictfp | default
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_Modifier , node.toString() , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>


        return false;
    }

    @Override
    public boolean visit(NameQualifiedType node) {
        // region <grammar>
        /**
         * Name . { Annotation } SimpleName
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_NameQualifiedType, "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        children = new ArrayList<CodeStructureTree>();
        //endregion <construct the tree of the root>

        //region <construct the tree of Name>
        Name qualifier = node.getQualifier();
        CodeVisitor qualifierVisitor = new CodeVisitor(tree);
        qualifier.accept(qualifierVisitor);
        children.add(qualifierVisitor.getTree());
        //region <construct the tree of>

        //region <construct the tree of Annotation>
        SimpleName name = node.getName();
        CodeVisitor nameVisitor = new CodeVisitor(tree);
        name.accept(nameVisitor);
        children.add(nameVisitor.getTree());
        //endregion <construct the tree of Annotation>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(NormalAnnotation node) {
        // region <grammar>
        /**
         *  NormalAnnotation:
         *      @ TypeName ( [ MemberValuePair { , MemberValuePair } ] )
         */
        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_NormalAnnotation , "" , id++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        // endregion <construct the tree of root>

        // region <construct the tree of @>
        children.add(
                buildPunctuationTree("@" , NodeType.ADDED_CHAR_AT , tree)
        );
        // endregion <construct the tree of @>

        // region <construct the tree of TypeName>
        Name typeName = node.getTypeName();
        CodeVisitor typeNameVisitor = new CodeVisitor(tree);
        typeName.accept(typeNameVisitor);
        children.add(typeNameVisitor.getTree());
        // endregion <construct the tree of TypeName>

        // region <construct the tree of ( >
        children.add(
                buildPunctuationTree("(" , NodeType.ADDED_CHAR_LEFT_PARENTHESIS , tree)
        );
        // endregion <construc the tree of ( >

        // region <construct the tree of MemberValuePairs>
        List<ASTNode> memberValuePairs = node.values();
        if(memberValuePairs != null && memberValuePairs.size() > 0){
            children.addAll(
                    batchProcess(memberValuePairs , "," , NodeType.ADDED_CHAR_COMMA , tree)
            );
        }
        // endregion <construct the tree of MemberValuePairs>

        // region <construct the tree of ) >
        children.add(
                buildPunctuationTree(")" , NodeType.ADDED_CHAR_RIGHT_PARENTHESIS , tree)
        );
        // endregion <construc the tree of ) >

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(NullLiteral node) {

        Node root = new Node(NodeType.CODE_NullLiteral , "null" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);

        return false;
    }

    @Override
    public boolean visit(NumberLiteral node) {
        //region<grammar>
        /**
         *
         */
        //endregion <grammar>

        Node root = new Node(NodeType.CODE_NumberLiteral , node.toString() , id ++);
        // remove the characters l L ...

        String temp = node.getToken().replaceAll("[^0-9-+.]" , "");
        if(temp.contains("+") || temp.contains(".") || temp.contains("+"))
            root.addAlternatives(temp);
        else
            root.addAlternatives(DigitsToWord.toEnglish(Integer.parseInt(temp)));

        tree = new CodeStructureTree(root , node.toString() , parent);


        return false;
    }

    @Override
    public boolean visit(PackageDeclaration node) {
        // region <grammar>
        /**
         *  [ Javadoc ] { Annotation } package Name ;
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_PackageDeclaration , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of Javadoc>
        Javadoc javadoc = node.getJavadoc();
        if(javadoc != null){
            CodeVisitor docVisitor = new CodeVisitor(tree);
            javadoc.accept(docVisitor);
            children.add(docVisitor.getTree());
        }
        //endregion <construct the tree of Javadoc>

        //region <construct the tree of Annotation>
        List<Annotation> annotations = node.annotations();
        if(annotations != null && annotations.size() > 0){
            for(Annotation annotation : annotations){
                CodeVisitor annotationVisitor = new CodeVisitor(tree);
                annotation.accept(annotationVisitor);
                children.add(annotationVisitor.getTree());
            }
        }
        //endregion <construct the tree of Annotation>

        //region <construct the tree of package>
        CodeStructureTree packageTree = buildKeyWordTree("package" , tree);
        children.add(packageTree);
        //endregion <construct the tree of package>

        //region <construct the tree of Name>
        Name name = node.getName();
        CodeVisitor nameVisitor = new CodeVisitor(tree);
        name.accept(nameVisitor);
        children.add(nameVisitor.getTree());
        //endregion <construct the tree of Name>

        //region <construct the tree of ;>
        CodeStructureTree semicolonTree = buildPunctuationTree(";" , NodeType.ADDED_CHAR_SEMICOLON , tree);
        children.add(semicolonTree);
        //endregion <construct the tree of ;>

        return false;
    }

    @Override
    public boolean visit(ParameterizedType node) {
        // region <grammar>
        /**
         *  ParameterizedType:
         *      Type < Type { , Type } >
         */
        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_ParameterizedType , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        // endregion <construct the tree of root>

        // region <construct the tree of parameterized type>
        Type parameterizedType = node.getType();
        CodeVisitor parameterizedTypeVisitor = new CodeVisitor(tree);
        parameterizedType.accept(parameterizedTypeVisitor);
        children.add(parameterizedTypeVisitor.getTree());
        // endregion <construct the tree of parameterized type >

        // region <construct the tree of < >
        children.add(
                buildPunctuationTree("<" , NodeType.ADDED_CHAR_LEFT_ANGLE_BRACKET ,tree)
        );
        // endregion <construct the tree of < >

        // region <construct the tree of types>
        List<ASTNode> types = node.typeArguments();
        children.addAll(
                batchProcess(types , "," , NodeType.ADDED_CHAR_COMMA , tree)
        );
        // endregion <construct the tree of types>

        // region <construct the tree of > >
        children.add(
                buildPunctuationTree(">" , NodeType.ADDED_CHAR_RIGHT_ANGLE_BRACKET , tree)
        );
        // endregion <construct the tree of > >

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(ParenthesizedExpression node) {
        // region <grammar>
        /**
         * ( Expression )
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_ParenthesizedExpression , "" , id++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of (>
        CodeStructureTree lpTree = buildPunctuationTree("(" , NodeType.ADDED_CHAR_LEFT_PARENTHESIS , tree);
        children.add(lpTree);
        //endregion <construct the tree of (>

        //region <construct the tree of Expression>
        Expression expression = node.getExpression();
        CodeVisitor expressionVisitor = new CodeVisitor(tree);
        expression.accept(expressionVisitor);
        children.add(expressionVisitor.getTree());
        //endregion <construct the tree of Expression>

        //region <construct the tree of )>
        CodeStructureTree rpTree = buildPunctuationTree(")" , NodeType.ADDED_CHAR_RIGHT_PARENTHESIS , tree);
        children.add(rpTree);
        //endregion <construct the tree of )>

        return false;
    }

    @Override
    public boolean visit(PostfixExpression node) {
        // region <grammar>
        /**
         * Expression PostfixOperator
         *
         * PostfixOperator：
         *  ++ | --
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_PostfixExpression , "" , id++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of Expression>
        Expression expression = node.getOperand();
        CodeVisitor expressionVisitor = new CodeVisitor(tree);
        expression.accept(expressionVisitor);
        children.add(expressionVisitor.getTree());
        //endregion <construct the tree of Expression>

        //region <construct the tree of PostfixOperator>
        Node operatorRoot = new Node(NodeType.CODE_PostfixExpression_Operator , node.getOperator().toString() , id ++);
        CodeStructureTree operatorTree = new CodeStructureTree(operatorRoot , node.getOperator().toString() , tree);
        children.add(operatorTree);
        //endregion <construct the tree of PostfixOperator>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(PrefixExpression node) {
        // region <grammar>
        /**
         * PrefixOperator Expression
         *
         * PrefixOperator:
         *  ++ | -- | + | - | ~ | !
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_PrefixExpression , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of PrefixOperator>
        Node prefixRoot = new Node(NodeType.CODE_PrefixExpression_Operator , node.getOperator().toString() , id ++);
        CodeStructureTree prefixTree = new CodeStructureTree(prefixRoot , node.getOperator().toString() , tree);
        children.add(prefixTree);
        //endregion <construct the tree of PrefixOperator>

        //region <construct the tree of Expression>
        Expression expression = node.getOperand();
        CodeVisitor expressionVisitor = new CodeVisitor(tree);
        expression.accept(expressionVisitor);
        children.add(expressionVisitor.getTree());
        //endregion <construct the tree of Expression>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(PrimitiveType node) {
        //region <grammar>
        /**
         * { Annotation } byte
         * { Annotation } short
         * { Annotation } char
         * { Annotation } int
         * { Annotation } long
         * { Annotation } float
         * { Annotation } double
         * { Annotation } boolean
         * { Annotation } void
         */
        //endregion <grammar>



        //region <construct the tree of Annotation>
        //Todo
        //endregion <construct the tree of Annotation>

        Node root = new Node(NodeType.CODE_PrimitiveType , node.getPrimitiveTypeCode().toString() , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);

        return false;
    }

    @Override
    public boolean visit(QualifiedName node) {
        // region <grammar>
        /**
         * Name . SimpleName
         */
        // endregion <grammar>
        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_QualifiedName , node.toString() , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        String[] names = node.toString().split("\\.");

        String firstName = names[0];
        String qualifiedName = firstName;
        Node firstNameRoot = new Node(NodeType.CODE_SimpleName, firstName, id ++);
        if(variableDictionary.containsKey(firstName)) {
            qualifiedName = variableDictionary.get(firstName);
            firstNameRoot.setAdditionalInfo(variableDictionary.get(firstName));
        }
        CodeStructureTree firstNameTree = new CodeStructureTree(firstNameRoot, firstName, tree);
        children.add(firstNameTree);
        CodeStructureTree dotTree = buildPunctuationTree(".", NodeType.ADDED_CHAR_DOT, tree);
        children.add(dotTree);




        for(int i = 1; i < names.length - 1 ; i++){


            Node nameRoot = new Node(NodeType.CODE_SimpleName, names[i] , id ++);
            CodeStructureTree nameTree = new CodeStructureTree(nameRoot, names[i], tree);
            children.add(nameTree);
            dotTree = buildPunctuationTree(".", NodeType.ADDED_CHAR_DOT, tree);
            children.add(dotTree);

            qualifiedName += ("." + names[i]);
        }


        String lastName = names[names.length - 1];
        Node lastNameRoot = new Node(NodeType.CODE_SimpleName, lastName, id++);

        Map<String , String> features = new HashMap<>();
        features.put("name" , lastName.toString());
        features.put("qualifiedName" , qualifiedName);
        Map<String , String> info = getMostPossibleAPIInfo(features);
        lastNameRoot.setAdditionalInfo(info.getOrDefault("javadoc" , ""));

        CodeStructureTree lastNameTree = new CodeStructureTree(lastNameRoot, lastName, tree);
        children.add(lastNameTree);

        tree.setChildren(children);


        /*
        String[] names = node.toString().split("\\.");

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_QualifiedName , node.toString() , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <add alternative info>
        root.addAlternatives(names[names.length - 1]);
        if(variableDictionary.containsKey(names[0])){
            names[0] = variableDictionary.get(names[0]);
            root.addAlternatives(String.join("." , names));
        }
        //endregion <and alternative info>

        //region <add additional info>
        SimpleName simpleName = node.getName();
        String[] qualifiedNames = node.getQualifier().toString().split("\\.");
        qualifiedNames[0] = variableDictionary.getOrDefault(qualifiedNames[0] , qualifiedNames[0]);
        Map<String , String> features = new HashMap<>();
        features.put("name" , simpleName.toString());
        features.put("qualifiedName" , String.join("." , qualifiedNames));
        Map<String , String> info = getMostPossibleAPIInfo(features);
        root.setAdditionalInfo(
                info.getOrDefault("javadoc" , "") + " " +
                String.join(" " , qualifiedNames )
        );
        //endregion <add additional info>*/

        return false;
    }

    @Override
    public boolean visit(QualifiedType node) {
        // region <grammar>
        /**
         * Type . {Annotation} SimpleName
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_QualifiedType , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        children = new ArrayList<CodeStructureTree>();
        //endregion <construct the tree of the root>

        //region <construct the tree of Type>
        Type qualifier = node.getQualifier();
        CodeVisitor qualifierVisitor = new CodeVisitor(tree);
        qualifier.accept(qualifierVisitor);
        children.add(qualifierVisitor.getTree());
        //region <construct the tree of Type>

        //region <construct the tree of Annotation>
        //Todo
        //region <construct the tree of Annotation>

        //region <construct the tree of SimpleName>
        SimpleName name = node.getName();
        CodeVisitor nameVisitor = new CodeVisitor(tree);
        name.accept(nameVisitor);
        children.add(nameVisitor.getTree());
        //region <construct the tree of SimpleName>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(ReturnStatement node) {
        // region <grammar>
        /**
         * return [ Expression ] ;
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_ReturnStatement , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        //region <construct the tree of return>
        CodeStructureTree returnTree = buildKeyWordTree("return" , tree);
        children.add(returnTree);
        //endregion <construct the tree of return>

        //region <construct the tree of >
        Expression expression = node.getExpression();
        if(expression != null) {
            CodeVisitor expressionVisitor = new CodeVisitor(tree);
            expression.accept(expressionVisitor);
            children.add(expressionVisitor.getTree());
        }
        //endregion <construct the tree of>

        //region <construct the tree of ;>
        CodeStructureTree semicolonTree = buildPunctuationTree(";" , NodeType.ADDED_CHAR_SEMICOLON , tree);
        children.add(semicolonTree);
        //endregion <construct the tree of ;>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(SimpleName node) {
        // region <grammar>
        /**
         * Identifier
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        String identifier = node.getIdentifier();
        Node root = new Node(NodeType.CODE_SimpleName ,identifier , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        return false;
    }

    @Override
    public boolean visit(SimpleType node) {
        // region <grammar>
        /**
         * { Annotation } TypeName
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_SimpleType , "" , id++);
        root.setAdditionalInfo(ClassJavadoc.getJavadoc(node.toString()));
        tree = new CodeStructureTree(root , node.toString() , parent);
        children = new ArrayList<CodeStructureTree>();

        Map<String, String> features = new HashMap<>();
        features.put("name" , node.toString());
        features.put("type" , "CLASS");



        //endregion <construct the tree of the root>

        //region <construct the tree of Annotation>
        //Todo
        //endregion <construct the tree of Annotation>

        //region <construct the tree of >
        Name name = node.getName();
        CodeVisitor nameVisitor = new CodeVisitor(tree);
        name.accept(nameVisitor);
        children.add(nameVisitor.getTree());
        //endregion <construct the tree of>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(SingleMemberAnnotation node) {

        // region <grammar>
        /**
         *  SingleMemberAnnotation:
         *      @ TypeName ( Expression  )
         */
        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_SingleMemberAnnotation , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        // endregion <construct the tree of root>

        // region <construct the tree of @>
        children.add(
                buildPunctuationTree("@" , NodeType.ADDED_CHAR_AT , tree)
        );
        // endregion <construct the tree of @>

        // region <construct the tree of TypeName>
        Name typeName = node.getTypeName();
        CodeVisitor typeNameVisitor = new CodeVisitor(tree);
        typeName.accept(typeNameVisitor);
        children.add(typeNameVisitor.getTree());
        // endregion <construct the tree of TypeName>

        // region <construct the tree of ( >
        children.add(
                buildPunctuationTree("(" , NodeType.ADDED_CHAR_LEFT_PARENTHESIS , tree)
        );
        // endregion <construct the tree of ( >

        // region <construct the tree of Expression>
        Expression expression = node.getValue();
        CodeVisitor expressionVisitor = new CodeVisitor(tree);
        expression.accept(expressionVisitor);
        children.add(expressionVisitor.getTree());
        // endregion <construct the tree of Expression>

        // region <construct the tree of ) >
        children.add(
                buildPunctuationTree(")" , NodeType.ADDED_CHAR_RIGHT_PARENTHESIS , tree )
        );
        // endregion <construct the tree of ) >



        return false;
    }

    @Override
    public boolean visit(SingleVariableDeclaration node) {
        // region <grammar>
        /**
         *  SingleVariableDeclaration:
         *      { ExtendedModifier } Type {Annotation} [ ... ] Identifier { Dimension } [ = Expression ]
         */

        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_SingleVariableDeclaration , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        // endregion <construct the tree of root>

        // region <construct the tree of  Type>
        Type type = node.getType();
        CodeVisitor typeVisitor = new CodeVisitor(tree);
        type.accept(typeVisitor);
        children.add(typeVisitor.getTree());
        // endregion <construct the tree of Type>

        // region <construct the tree of Identifier>
        SimpleName name = node.getName();
        CodeVisitor nameVisitor = new CodeVisitor(tree);
        name.accept(nameVisitor);
        children.add(nameVisitor.getTree());
        // endregion <construct the tree of Identifier>

        // region <construct the tree of Dimension>
        List<Dimension> dimensions = node.extraDimensions();
        if(dimensions != null && dimensions.size() > 0) {
            for (Dimension dimension : dimensions) {
                CodeVisitor dimensionVisitor = new CodeVisitor(tree);
                dimension.accept(dimensionVisitor);
                children.add(dimensionVisitor.getTree());
            }
        }
        // endregion <construct the tree of Dimension>

        // region <construct the tree of Expression>
        Expression expression = node.getInitializer();
        if(expression != null) {
            CodeStructureTree equalTree = buildPunctuationTree("=", NodeType.CODE_InfixExpression_OPERATOR_EQUALS , tree);
            children.add(equalTree);

            CodeVisitor expressionVisitor = new CodeVisitor(tree);
            expression.accept(expressionVisitor);
            children.add(expressionVisitor.getTree());
        }
        // endregion <construct the tree of Expression>


        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(StringLiteral node) {
        String literal = node.toString();
        literal = literal.length() == 2 ? "" : literal.substring(1 , literal.length() - 1);
        Node root = new Node(NodeType.CODE_StringLiteral , literal , id ++);
        root.setDisplayContent(node.toString()); // 显示的是带引号的，如"test" , 实际匹配的时候用的是不带引号的， 如test.
        String camelCasePattern = "([^\\p{L}\\d]+)|(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)|(?<=[\\p{L}&&[^\\p{Lu}]])(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}[\\p{L}&&[^\\p{Lu}]])";
        root.addAlternatives(
                String.join("" ,
                        node.toString().split(camelCasePattern))
        );

        StringBuilder content = new StringBuilder(node.toString());
        String additionalInfo = content.substring(1 , content.length() - 1).replaceAll("[{|}]" , " curly bracket ").
                replaceAll("[\\[|\\]]" , " square bracket ").
                replaceAll(":" , " colon ").
                replaceAll("\\." , " period ").
                replaceAll("," , " comma ").
                replaceAll("\\?" , " question mark ").
                replaceAll("\\.\\.\\." , " ellipsis ").
                replaceAll("@" , " at ").
                replaceAll("\\*" , " asterisk ").
                replaceAll("['|\"]" , " quotation mark ");

        if(additionalInfo.compareTo(node.toString())!= 0)
            root.setAdditionalInfo(additionalInfo);

        tree = new CodeStructureTree(root ,  node.toString() , parent);

        return false;
    }

    @Override
    public boolean visit(SuperConstructorInvocation node) {
        // region <grammar>
        /**
         *  SuperConstructorInvocation:
         *      [ Expression . ] [ < Type { , Type } > ] super ( [ Expression { , Expression } ] ) ;
         */
        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_SuperMethodInvocation , ""  , id++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        // endregion <construct the tree of root>

        // region <construct the tree of Expression>
        Expression expression = node.getExpression();
        if(expression != null){
            CodeVisitor expressionVisitor = new CodeVisitor(tree);
            expression.accept(expressionVisitor);
            children.add(expressionVisitor.getTree());

            children.add(
                    buildPunctuationTree("." , NodeType.ADDED_CHAR_DOT , tree)
            );
        }
        // endregion <construct the tree of Expression>

        // region <construct the tree of types>
        List<ASTNode> types = node.typeArguments();
        if(types != null && types.size() > 0){
            children.add(
                    buildPunctuationTree("<" , NodeType.ADDED_CHAR_LEFT_ANGLE_BRACKET , tree)
            );

            children.addAll(
                    batchProcess(types , "," , NodeType.ADDED_CHAR_COMMA , tree)
            );

            children.add(
                    buildPunctuationTree(">" , NodeType.ADDED_CHAR_RIGHT_ANGLE_BRACKET , tree)
            );
        }

        // endregion <construct the tree of types>

        // region <construct the tree of super>
        children.add(
                buildKeyWordTree("super" , tree)
        );
        // endregion <construct the tree of super>

        // region <construct the tree of ( >
        children.add(
                buildPunctuationTree("(" , NodeType.ADDED_CHAR_LEFT_PARENTHESIS , tree)
        );
        // endregion <construct the tree of ( >

        // region <construct the tree of expression arguments>
        List<ASTNode> expressionArguments = node.arguments();
        if(expressionArguments != null && expressionArguments.size() > 0){
            children.addAll(
                    batchProcess(expressionArguments , "," , NodeType.ADDED_CHAR_COMMA , tree)
            );
        }
        // endregion <construct the tree of expression arguments>

        // region <construct the tree of ) >
        children.add(
                buildPunctuationTree("(" , NodeType.ADDED_CHAR_RIGHT_PARENTHESIS , tree)
        );
        // endregion <construct the tree of ) >

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(SuperFieldAccess node) {
        // region <grammar>
        /**
         *  SuperFieldAccess:
         *      [ ClassName . ] super . Identifier
         */
        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_SuperFieldAccess , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        // endregion <construct the tree of root>

        // region <construct the tree of ClassName>
        Name className = node.getName();
        if(className != null) {
            CodeVisitor classNameVisitor = new CodeVisitor(tree);
            className.accept(classNameVisitor);
            children.add(classNameVisitor.getTree());

            // region <construct the tree of .>
            CodeStructureTree dotTree = buildPunctuationTree("." , NodeType.ADDED_CHAR_DOT , tree);
            children.add(dotTree);
            // endregion <construct the tree of .>
        }
        // endregion <construct the tree of ClassName>

        // region <construct the tree of super>
        CodeStructureTree superTree = buildKeyWordTree("super" , tree);
        children.add(superTree);
        // endregion <construct the tree of super>

        // region <construct the tree of .>
        CodeStructureTree dotTree = buildPunctuationTree("." , NodeType.ADDED_CHAR_DOT , tree);
        children.add(dotTree);
        // endregion <construct the tree of .>

        // region <construct the tree of Identifier>
        SimpleName identifier = node.getName();
        CodeVisitor identifierVisitor = new CodeVisitor(tree);
        identifier.accept(identifierVisitor);
        children.add(identifierVisitor.getTree());
        // endregion <construct the tree of Identifier>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(SuperMethodInvocation node) {
        //region <grammar>
        /**
         *  SuperMethodInvocation:
         *      [ ClassName . ] super . [ < Type { , Type } > ] Identifier ( [ Expression { , Expression } ] )
         */
        //endregion <grammar>

        //region <construct the tree of root>
        Node root = new Node(NodeType.CODE_SuperMethodInvocation , "" , id++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of root>

        //region <construct the tree of ClassName>
        Name className = node.getQualifier();
        if(className != null) {
            CodeVisitor classNameVisitor = new CodeVisitor(tree);
            className.accept(classNameVisitor);
            children.add(classNameVisitor.getTree());
        }
        //endregion <construct the tree of ClassName>

        //region <construct the tree of super>
        CodeStructureTree superTree = buildKeyWordTree("super" , tree);
        children.add(superTree);
        //endregion <construct the tree of super>

        //region <construct the tree of .>
        CodeStructureTree dotTree = buildPunctuationTree("." , NodeType.ADDED_CHAR_DOT , tree);
        children.add(dotTree);
        //endregion <construct the tree of .>

        //region <construct the tree of <Type>>
        List<ASTNode> types = node.typeArguments();
        if(types != null && types.size() > 0){
            // region <construct the tree of < >
            CodeStructureTree labTree = buildPunctuationTree("<" , NodeType.ADDED_CHAR_LEFT_ANGLE_BRACKET , tree);
            children.add(labTree);
            // endregion <construct the tree of < >

            List<CodeStructureTree> typeTrees = batchProcess(types , "," , NodeType.ADDED_CHAR_COMMA , tree);
            children.addAll(typeTrees);

            // region <construct the tree of < >
            CodeStructureTree rabTree = buildPunctuationTree(">" , NodeType.ADDED_CHAR_RIGHT_ANGLE_BRACKET , tree);
            children.add(rabTree);
            // endregion <construct the tree of < >
        }
        //endregion <construct the tree of <Type>>

        //region <construct the tree of Expression>
        List<ASTNode> arguments = node.arguments();
        if(arguments != null && arguments.size() > 0){
            List<CodeStructureTree> argumentTrees = batchProcess(arguments , "," , NodeType.ADDED_CHAR_COMMA , tree);
            children.addAll(argumentTrees);
        }
        //endregion <construct the tree of Expression>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(SuperMethodReference node) {
        // region <grammar>
        /**
         *  SuperMethodReference:
         *      [ ClassName . ] super :: [ < Type { , Type } > ] Identifier
         */
        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_SuperMethodReference , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        // endregion <construct the tree of root>

        // region <construct the tree of ClassName>
        Name className = node.getQualifier();
        if(className != null){
            CodeVisitor classNameVisitor = new CodeVisitor(tree);
            className.accept(classNameVisitor);
            children.add(classNameVisitor.getTree());

            // region <construct the tree of .>
            CodeStructureTree dotTree = buildPunctuationTree("." , NodeType.ADDED_CHAR_DOT , tree);
            children.add(dotTree);
            // endregion <construct the tree of .>
        }
        // endregion <construct the tree of ClassName>

        // region <construct the tree of super>
        CodeStructureTree superTree = buildKeyWordTree("super" , tree);
        children.add(superTree);
        // endregion <construct the tree of super>

        // region <construct the tree of ::>
        CodeStructureTree doubleColonTree = buildPunctuationTree("::" , NodeType.ADDED_CHAR_DOUBLE_COLON , tree);
        children.add(doubleColonTree);
        // endregion <construct the tree of ::>

        // region <construct the tree of types>
        List<ASTNode> types = node.typeArguments();
        if(types != null && types.size() > 0){
            // region <construct the tree < >
            CodeStructureTree labTree = buildPunctuationTree("<" , NodeType.ADDED_CHAR_LEFT_ANGLE_BRACKET , tree);
            children.add(labTree);
            // endregion <construct the tree < >

            // region <construct the tree of types>
            List<CodeStructureTree> typeTrees = batchProcess(types , "," , NodeType.ADDED_CHAR_COMMA , tree);
            children.addAll(typeTrees);
            // endregion <construct the tree of types>

            // region <construct the tree > >
            CodeStructureTree rabTree = buildPunctuationTree(">" , NodeType.ADDED_CHAR_RIGHT_ANGLE_BRACKET , tree);
            children.add(rabTree);
            // endregion <construct the tree > >
        }
        // endregion <construct the tree of types>

        // region <construct the tree of Identifier>
        SimpleName identifier = node.getName();
        CodeVisitor identifierVisitor = new CodeVisitor(tree);
        identifier.accept(identifierVisitor);
        children.add(identifierVisitor.getTree());
        // endregion <construct the tree of Identifier>


        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(SwitchCase node) {
        // region <grammar>
        /**
         *  SSwitchCase:
         *      case Expression  :
         *      default :
         */
        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_SwitchCase , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        // endregion <construct the tree of root>

        Expression optionalExpression = node.getExpression();

        // case Expression :
        if(optionalExpression != null){
            // region <construct the tree of case>
            CodeStructureTree caseTree = buildKeyWordTree("case" , tree);
            children.add(caseTree);
            // endregion <construct the tree of case>

            // region <construct the tree of Expression>
            CodeVisitor expressionVisitor = new CodeVisitor(tree);
            optionalExpression.accept(expressionVisitor);
            CodeStructureTree expressionTree = expressionVisitor.getTree();
            children.add(expressionTree);
            // endregion <construct the tree of Expression>

            // region <construct the  tree of :>
            CodeStructureTree colonTree = buildPunctuationTree(":" , NodeType.ADDED_CHAR_COLON , tree);
            children.add(colonTree);
            // endregion <construct the  tree of :>

        }else{// default :
            // region <construct the tree of default>
            CodeStructureTree defaultTree = buildKeyWordTree("default" , tree);
            children.add(defaultTree);
            // endregion <construct the tree of default>

            // region <construct the tree of colon>
            CodeStructureTree colonTree = buildPunctuationTree(":" , NodeType.ADDED_CHAR_COLON , tree);
            children.add(colonTree);
            // endregion <construct the tree of colon>

        }

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(SwitchStatement node) {
        // region <grammar>
        /**
         *  SwitchStatement:
         *      switch ( Expression )
         *          { { SwitchCase | Statement } }
         *  SwitchCase:
         *      case Expression  :
         *      default :
         */
        // endregion <grammar>

        // region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_SwitchStatement , "" , id ++);
        String code = "switch (" + node.getExpression().toString() + ")";
        tree = new CodeStructureTree(root , code , parent);
        tree.setProperty("size" , node.getProperty("size"));
        tree.setProperty("start" , node.getProperty("start"));
        tree.setProperty("end" , node.getProperty("end"));
        // endregion <construct the tree of the root>

        // region <construct the tree of (>
        CodeStructureTree lpTree = buildPunctuationTree("(" , NodeType.ADDED_CHAR_LEFT_PARENTHESIS , tree);
        children.add(lpTree);
        // endregion <construct the tree of (>

        // region <construct the tree of Expression>
        Expression expression = node.getExpression();
        if(expression != null){
            CodeVisitor expressionVisitor = new CodeVisitor(tree);
            expression.accept(expressionVisitor);
            CodeStructureTree expressionTree = expressionVisitor.getTree();
            children.add(expressionTree);
        }
        // endregion <construct the tree of Expression>

        // region <construct the tree of )>
        CodeStructureTree rpTree = buildPunctuationTree(")" , NodeType.ADDED_CHAR_RIGHT_PARENTHESIS, tree);
        children.add(rpTree);
        // endregion <construct the tree of )>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(SynchronizedStatement node) {
        // region <grammar>
        /**
         *  SynchronizedStatement:
         *      synchronized ( Expression ) Block
         */
        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_SynchronizedStatement , "" , id++);
        String code = "synchronized (" + node.getExpression() +")" ;
        this.tree = new CodeStructureTree(root ,code , parent);
        tree.setProperty("size" , node.getProperty("size"));
        tree.setProperty("start" , node.getProperty("start"));
        tree.setProperty("end" , node.getProperty("end"));
        // endregion <construct the tree of root>

        // region <construct the tree of (>
        CodeStructureTree lpTree = buildPunctuationTree("(" , NodeType.ADDED_CHAR_LEFT_PARENTHESIS , tree);
        children.add(lpTree);
        // endregion <construct the tree of (>

        // region <construct the tree of Expression>
        Expression expression = node.getExpression();
        CodeVisitor expressionVisitor = new CodeVisitor(tree);
        expression.accept(expressionVisitor);
        CodeStructureTree expressionTree = expressionVisitor.getTree();
        children.add(expressionTree);
        // endregion <construct the tree of Expression>

        // region <construct the tree of )>
        CodeStructureTree rpTree = buildPunctuationTree(")" , NodeType.ADDED_CHAR_RIGHT_PARENTHESIS , tree);
        children.add(rpTree);
        // endregion <construct the tree of )>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(TagElement node) {
        return false;
    }

    @Override
    public boolean visit(TextElement node) {
        return false;
    }

    @Override
    public boolean visit(ThisExpression node) {

        // region <grammar>
        /**
         *  ThisExpression:
         *      [ ClassName . ] this
         */
        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_ThisExpression , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        // endregion <construct the tree of root>

        // region <construct the tree of ClassName>
        Name qualifiedName = node.getQualifier();
        if(qualifiedName != null){
            CodeVisitor nameVisitor  = new CodeVisitor(tree);
            qualifiedName.accept(nameVisitor);
            children.add(nameVisitor.getTree());

            CodeStructureTree dotTree = buildPunctuationTree("." , NodeType.ADDED_CHAR_DOT , tree);
            children.add(dotTree);
        }
        // region <construct the tree of ClassName>

        // region <construct the tree of this>
        CodeStructureTree thisTree = buildKeyWordTree("this" , tree);
        children.add(thisTree);
        // endregion <construct the tree of this>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(ThrowStatement node) {
        // region <grammar>
        /**
         *  ThrowStatement:
         *      throw Expression ;
         */
        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_ThrowStatement , "" , id++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        // endregion <construct the tree of root>

        // region <construct the tree of throw>
        CodeStructureTree throwTree = buildKeyWordTree("throw" , tree);
        children.add(throwTree);
        // endregion <construct the tree of throw>

        // region <construct the tree of Expression>
        Expression expression = node.getExpression();
        CodeVisitor expressionVisitor = new CodeVisitor(tree);
        expression.accept(expressionVisitor);
        children.add(expressionVisitor.getTree());
        // endregion <construct the tree of Exression >

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(TryStatement node) {
        // region <grammar>
        /**
         *  try [ ( Resources ) ]
         *      Block
         *      [ { CatchClause } ]
         *      [ finally Block ]
         */
        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_TryStatement , "" , id++);
        this.tree = new CodeStructureTree(root , "try" , parent);
        tree.setProperty("size" , node.getProperty("size"));
        tree.setProperty("start" , node.getProperty("start"));
        tree.setProperty("end" , node.getProperty("end"));
        // endregion <construct the tree of root>

        // 结点为try 语句
        if(node.getBody() != null){
            CodeStructureTree tryTree = buildKeyWordTree("try" , tree);
            children.add(tryTree);
        }else{ // 结点为 finally 语句
            CodeStructureTree finallyTree = buildKeyWordTree("finally" , tree);
            children.add(finallyTree);
        }

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        return false;
    }

    @Override
    public boolean visit(TypeDeclarationStatement node) {
        return false;
    }

    @Override
    public boolean visit(TypeLiteral node) {
        //region<grammar>
        /**
         * ( Type | void ) . class
         */
        //endregion<grammar>

        //region<construct the tree of root>
        Node root = new Node(NodeType.CODE_TypeLiteral , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion<construct the tree of root>

        //region<construct the tree of Type>
        Node typeNode = new Node(NodeType.NULL , node.getType().toString() , id ++);
        CodeStructureTree typeTree = new CodeStructureTree(typeNode , node.getType().toString() , tree);
        children.add(typeTree);
        //endregion<construct the tree of Type>

        //region<construct the tree of .>
        children.add(buildPunctuationTree("." , NodeType.ADDED_CHAR_DOT , tree));
        //endregion <construct the tree of .>

        //region<construct the tree of class>
        Node classRoot = new Node(NodeType.NULL , "class" , id++);
        CodeStructureTree classTree = new CodeStructureTree(classRoot , "class" , tree);
        children.add(classTree);
        //endregion<construct the tree of class>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(TypeMethodReference node) {
        return false;
    }

    @Override
    public boolean visit(TypeParameter node) {
        return false;
    }

    @Override
    public boolean visit(UnionType node) {
        // region <grammar>
        /**
         *  Type | Type { | Type }
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_UnionType , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        children = new ArrayList<CodeStructureTree>();
        //endregion <construct the tree of the root>

        //region <construct the tree of Types>
        List<Type> types = node.types();
        if(types != null){
            for(Type type : types){
                CodeVisitor typeVisitor = new CodeVisitor(tree);
                type.accept(typeVisitor);
                children.add(typeVisitor.getTree());
            }
        }
        //region <construct the tree of Types>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationExpression node) {
        // region <grammar>
        /**
         *  VariableDeclarationExpression:
         *      { ExtendedModifier } Type VariableDeclarationFragment{ , VariableDeclarationFragment }
         */
        // endregion <grammar>

        //region <construct the tree of the root>
        Node root = new Node(NodeType.CODE_VariableDeclarationExpression , "" , id++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of the root>

        // region <construct the tree Type>
        Type type = node.getType();
        CodeVisitor typeVisitor = new CodeVisitor(tree);
        type.accept(typeVisitor);
        children.add(typeVisitor.getTree());
        // endregion <construct the tree Type>

        // region <construct the tree of VariableDeclarationFragment>
        List<ASTNode> fragments = node.fragments();
        List<CodeStructureTree> fragmentTrees = batchProcess(fragments , "," , NodeType.ADDED_CHAR_COMMA , tree);
        children.addAll(fragmentTrees);
        // endregion <construct the tree of VariableDeclarationFragment>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationStatement node) {
        //region <grammar>
        /**
         * { ExtendedModifier } Type VariableDeclarationFragment { , VariableDeclarationFragment } ;
         */
        //endregion <grammar>

        String typeName ;


        //region <construct the tree of root>
        Node root = new Node(NodeType.CODE_VariableDeclarationStatement , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of root>

        //region <construct the tree of ExtendedModifier>
        List<IExtendedModifier> modifiers = node.modifiers();
        if(modifiers != null && modifiers.size() > 0){
            for(IExtendedModifier modifier : modifiers){
                Node modifierRoot = new Node(NodeType.CODE_Modifier , modifier.toString() , id ++);
                CodeStructureTree modifierTree = new CodeStructureTree(modifierRoot , modifier.toString() , tree);
                children.add(modifierTree);
            }
        }
        //endregion <construct the tree of ExtendedModifier>

        //region <construct the tree of Type>
        Type type = node.getType();
        CodeVisitor typeVisitor = new CodeVisitor(tree);
        type.accept(typeVisitor);
        children.add(typeVisitor.getTree());

        typeName = type.toString();
        //endregion <construct the tree of Type>

        //region <construct the tree of VariableDeclarationFragments>
        List<ASTNode> fragments = node.fragments();
        for(ASTNode fragment : fragments){
            String variableName = ((VariableDeclarationFragment)fragment).getName().toString();
            variableDictionary.put(variableName , typeName);
        }

        List<CodeStructureTree> fragmentTrees = batchProcess(fragments , "," , NodeType.ADDED_CHAR_COMMA , tree);
        children.addAll(fragmentTrees);


        //endregion <construct the tree of VariableDeclarationFragments>

        //region <construct the tree of ; >
        Node semicolonRoot = new Node(NodeType.ADDED_CHAR_SEMICOLON , ";" , id ++);
        CodeStructureTree semicolonTree = new CodeStructureTree(semicolonRoot , ";" , tree);
        children.add(semicolonTree);
        //endregion <construct the tree of ;>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(VariableDeclarationFragment node) {
        //region <grammar>
        /**
         * Identifier { Dimension } [ = Expression ]
         */
        //endregion <grammar>

        //region <construct the tree of root>
        Node root = new Node(NodeType.CODE_VariableDeclarationFragment , "" , id ++);
        tree = new CodeStructureTree(root , node.toString() , parent);
        //endregion <construct the tree of root>

        //region <construct the tree of Identifier>
        SimpleName identifier = node.getName();
        CodeVisitor identifierVisitor = new CodeVisitor(tree);
        identifier.accept(identifierVisitor);
        children.add(identifierVisitor.getTree());
        //endregion <construct the tree of Identifier>

        //region <construct the tree of Dimension>
        List<Dimension> dimensions = node.extraDimensions();
        if(dimensions != null && dimensions.size() != 0){
            for(Dimension dimension : dimensions){
                CodeVisitor dimensionVisitor = new CodeVisitor(tree);
                dimension.accept(dimensionVisitor);
                children.add(dimensionVisitor.getTree());
            }
        }
        //endregion <construct the tree of Dimension>

        //region <construct the tree of expression>
        Expression expression = node.getInitializer();
        if(expression != null) {
            //region <construct the tree of =>
            Node equalRoot = new Node(NodeType.CODE_InfixExpression_OPERATOR_EQUALS , "=" , id ++);
            CodeStructureTree equalTree = new CodeStructureTree(equalRoot , "=" , tree);
            children.add(equalTree);
            //endregion <construct the tree of =>

            CodeVisitor expressionVisitor = new CodeVisitor(tree);
            expression.accept(expressionVisitor);
            children.add(expressionVisitor.getTree());
        }
        //endregion <construct the tree of expression>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(WhileStatement node) {
        // region <grammar>
        /**
         *  WhileStatement:
         *      while ( Expression ) Statement
         */
        // endregion <grammar>

        // region <construct the tree of root>
        Node root = new Node(NodeType.CODE_WhileStatement , "" , id ++);
        String code = "while (" + node.getExpression().toString() + ")" ;
        this.tree = new CodeStructureTree(root , code , parent);
        tree.setProperty("size" , node.getProperty("size"));
        tree.setProperty("start" , node.getProperty("start"));
        tree.setProperty("end" , node.getProperty("end"));
        // endregion <construct the tree of root>

        // region <construct the tree of while>
        CodeStructureTree whileTree = buildKeyWordTree("while" , tree);
        children.add(whileTree);
        // endregion <construct the tree of while>

        // region <construct the tree of (>
        CodeStructureTree lpTree = buildPunctuationTree("(" , NodeType.ADDED_CHAR_LEFT_PARENTHESIS , tree);
        children.add(lpTree);
        // endregion <construct the tree of (>

        // region <construct the tree of Expression>
        Expression expression = node.getExpression();
        CodeVisitor expressionVisitor = new CodeVisitor(tree);
        expression.accept(expressionVisitor);
        CodeStructureTree expressionTree = expressionVisitor.getTree();
        children.add(expressionTree);
        // endregion <construct the tree of Expression>

        // region <construct the tree of )>
        CodeStructureTree rpTree = buildPunctuationTree(")" , NodeType.ADDED_CHAR_RIGHT_PARENTHESIS , tree);
        children.add(rpTree);
        // endregion <construct the tree of )>

        tree.setChildren(children);
        return false;
    }

    @Override
    public boolean visit(WildcardType node) {
        return false;
    }

    private CodeStructureTree buildKeyWordTree(String keyWord , CodeStructureTree parentTree){
        Node root = new Node(NodeType.ADDED_KEYWORD , keyWord , id ++);
        return new CodeStructureTree(root , keyWord , parentTree);
    }

    private CodeStructureTree buildPunctuationTree(String c , NodeType type , CodeStructureTree parentTree){
        Node root = new Node(type , c , id ++);
        return new CodeStructureTree(root , c , parentTree);
    }

    private Map<String , String> getMostPossibleMethodInfo(MethodInvocation node){
        Map<String , String> result = null;

        String methodName = node.getName().toString();
        String methodQualifiedName = node.getExpression() == null ? "" : node.getExpression().toString();
        if(variableDictionary.containsKey(methodQualifiedName)) {
            methodQualifiedName = variableDictionary.get(methodQualifiedName);
        }

        List<ASTNode> methodArguments = node.arguments();
        int argumentCount = methodArguments.size();
        List<Pair<String , String>> methodArgumentList = new ArrayList<>();
        String argumentTypes = "";
        for(ASTNode argument : methodArguments){
            // <name , type>
            Pair<String , String> pair = new Pair<>(argument.toString() , getTypeName(argument));
            methodArgumentList.add(pair);
            argumentTypes = pair.getValue() + " | ";
        }
        if(argumentTypes.length() > 0)
            // remove the ending ' | '
            argumentTypes = argumentTypes.substring(0, argumentTypes.length() - 3);

        try {
            StringBuilder sql = new StringBuilder("select name , qualifiedName , returnType , argumentTypes , argumentNames , javadoc from api where ");
            sql.append("name = '" + methodName + "' ");
            sql.append("and qualifiedName = '" + methodQualifiedName + "' ");
            sql.append("and argumentCount = " + argumentCount + " ");
            sql.append("and argumentTypes = '" + argumentTypes + "'");

            conn.setPreparedStatement(sql.toString());
            ResultSet rs = conn.executeQuery();

            int score = 0;
            int max = 0;
            while (rs.next()) {
                score = 0;

                String candidateQualifiedName = rs.getString("qualifiedName");
                int index = candidateQualifiedName.lastIndexOf(".");
                index = index == -1 ? 0 : index + 1;
                candidateQualifiedName = candidateQualifiedName.substring(index);

                String[] candidateArgumentTypes = rs.getString(4).split(" \\| ");
                String[] candidateArgumentNames = rs.getString(5).split(" \\| ");

                if(methodQualifiedName.length() > 0){
                    if(methodQualifiedName.compareTo(candidateQualifiedName) == 0)
                        score ++;
                    else if(methodQualifiedName.contains(candidateQualifiedName) ||
                            candidateQualifiedName.contains(methodQualifiedName))
                        score += 0.1;
                    else
                        continue;
                }

                boolean signal = true;
                for(int i = 0 ; i < argumentCount ; i++){
                    Pair<String , String> argumentPair = methodArgumentList.get(i);
                    String name = argumentPair.getKey();
                    String type = argumentPair.getValue();

                    if(type.length() > 0){
                        index = type.lastIndexOf(".");
                        if(index > 0) type = type.substring(index + 1);
                        if(type.compareTo(candidateArgumentTypes[i]) == 0){
                            score ++ ;
                        }else if(type.contains(candidateArgumentTypes[i]) || candidateArgumentTypes[i].contains(type)){
                            score += 0.1;
                        }else{
                            signal = false;
                            break;
                        }

                    }
                }
                if(!signal) continue;


                if(score > max){
                    max = score;
                    if(result == null)
                        result = new HashMap<>();
                    result.put("name" , rs.getString(1));
                    result.put("qualifiedName" , rs.getString(2));
                    result.put("returnType" , rs.getString(3));
                    result.put("argumentTypes" , rs.getString(4));
                    result.put("argumentNames" , rs.getString(5));
                    result.put("javadoc" , rs.getString(6));
                    break;
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        return result;

    }

    private String getTypeName(ASTNode node){
        try {
            if (node instanceof ArrayAccess){
                // ArrayAccess: Expression [ Expression ]
                return getTypeName(((ArrayAccess) node).getArray());
            }else if(node instanceof ArrayCreation){
                return getTypeName(((ArrayCreation) node).getType());
            }else if(node instanceof Assignment){
                return getTypeName(((Assignment) node).getLeftHandSide());
            }else if(node instanceof BooleanLiteral){
                return "boolean";
            }else if(node instanceof CastExpression){
                return getTypeName(((CastExpression) node).getType());
            }else if(node instanceof CharacterLiteral){
                return "char";
            }else if(node instanceof ClassInstanceCreation){
                return getTypeName(((ClassInstanceCreation) node).getType());
            }else if(node instanceof ConditionalExpression){
                // Expression ? Expression : Expression
                String result = getTypeName(((ConditionalExpression) node).getThenExpression());
                return result.compareTo("null") != 0 ? result : getTypeName(((ConditionalExpression) node).getElseExpression());
            }else if(node instanceof InfixExpression){
                InfixExpression.Operator op = ((InfixExpression) node).getOperator();
                if(op == InfixExpression.Operator.TIMES ||
                        op == InfixExpression.Operator.DIVIDE ||
                        op == InfixExpression.Operator.PLUS ||
                        op == InfixExpression.Operator.MINUS ||
                        op == InfixExpression.Operator.REMAINDER){
                    return getTypeName(((InfixExpression) node).getLeftOperand());
                }else if(op == InfixExpression.Operator.LESS ||
                        op == InfixExpression.Operator.GREATER ||
                        op == InfixExpression.Operator.LESS_EQUALS ||
                        op == InfixExpression.Operator.GREATER_EQUALS ||
                        op == InfixExpression.Operator.EQUALS ||
                        op == InfixExpression.Operator.NOT_EQUALS ||
                        op == InfixExpression.Operator.CONDITIONAL_AND ||
                        op == InfixExpression.Operator.CONDITIONAL_OR){
                    return "boolean";
                }else if(op == InfixExpression.Operator.LEFT_SHIFT ||
                        op == InfixExpression.Operator.RIGHT_SHIFT_SIGNED ||
                        op == InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED){
                    return getTypeName(((InfixExpression) node).getLeftOperand());
                }else if(op == InfixExpression.Operator.XOR ||
                        op == InfixExpression.Operator.AND ||
                        op == InfixExpression.Operator.OR){
                    return getTypeName(((InfixExpression) node).getLeftOperand());
                }else{
                    return "";
                }
            }else if(node instanceof InstanceofExpression){
                return "boolean";
            }else if(node instanceof Name){
                String name = node.toString();
                return variableDictionary.getOrDefault(name , "");
            }else if(node instanceof NullLiteral){
                return "null";
            }else if(node instanceof NumberLiteral){
                return node.toString().contains(".") ? "double" : "int";
            }else if(node instanceof ParenthesizedExpression){
                return getTypeName(((ParenthesizedExpression) node).getExpression());
            }else if(node instanceof PostfixExpression){
                return getTypeName(((PostfixExpression) node).getOperand());
            }else if(node instanceof PrefixExpression){
                return getTypeName(((PrefixExpression) node).getOperand());
            }else if(node instanceof StringLiteral){
                return "String";
            }else if(node instanceof VariableDeclarationExpression){
                return getTypeName(((VariableDeclarationExpression) node).getType());
            }else if(node instanceof PrimitiveType){
                return node.toString();
            }else if(node instanceof ArrayType){
                return getTypeName(((ArrayType) node).getElementType()) + "[]";
            }else if(node instanceof SimpleType){
                return node.toString();
            }else if(node instanceof QualifiedType){
                return ((QualifiedType) node).getName().toString();
            }else if(node instanceof NameQualifiedType){
                return ((NameQualifiedType) node).getName().toString();
            }else if(node instanceof ParameterizedType){
                return getTypeName(((ParameterizedType) node).getType()) + " < >";
            }else if(node instanceof UnionType || node instanceof IntersectionType){
                Type type ;
                if(node instanceof UnionType)
                    type = (Type)((UnionType) node).types().get(0);
                else
                    type = (Type)((IntersectionType) node).types().get(0);

                return getTypeName(type);
            }else {
                return "";
            }
        }catch (Exception e){
            e.printStackTrace();
            return "";
        }
    }

    private Map<String , String> getMostPossibleAPIInfo(Map<String , String> features){
        Map<String , String> result = new HashMap<>();

        StringBuilder sql = new StringBuilder("select * from " );
        sql.append(tableName).append(" where ");
        if(features.size() > 0) {
            for (String feature : features.keySet()) {
                sql.append(" binary ").append(feature).append(" = '").append(features.get(feature)).append("' and ");
            }
            sql.delete( sql.length() - 5 , sql.length());

            conn.setPreparedStatement(sql.toString());

            try{
                ResultSet rs = conn.executeQuery();
                rs.last();
                int rowNumber = rs.getRow();
                if(rowNumber == 1) {
                    result.put("name" , rs.getString("name"));
                    result.put("stemmedName" , rs.getString("stemmedName"));
                    result.put("packageName" , rs.getString("packageName"));
                    result.put("qualifiedName" , rs.getString("qualifiedName"));
                    result.put("type" , rs.getString("type"));
                    result.put("fieldType" , rs.getString("fieldType"));
                    result.put("extends" , rs.getString("extends"));
                    result.put("implements" , rs.getString("implements"));
                    result.put("returnType" , rs.getString("returnType"));
                    result.put("argumentCount" , rs.getString("argumentCount"));
                    result.put("argumentTypes" , rs.getString("argumentTypes"));
                    result.put("argumentNames" , rs.getString("argumentNames"));
                    result.put("javadoc" , rs.getString("javadoc") );
                }else if(rowNumber > 1){
                    System.out.println("Warning from CodeVisitor.getMostPossibleAPIInfo:");
                    System.out.print("  There are more than 1 result: ");
                    for(String feature : features.keySet()){
                        System.out.print(feature + " = '" + features.get(feature) + "' ");
                    }
                    System.out.println();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        return result;
    }
}

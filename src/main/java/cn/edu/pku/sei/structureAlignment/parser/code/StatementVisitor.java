package cn.edu.pku.sei.structureAlignment.parser.code;

import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by oliver on 2018/3/7.
 */
public class StatementVisitor extends ASTVisitor{
    List<ASTNode> blockStatements;

    public List<ASTNode> getStatements(){
        return blockStatements;
    }

    public StatementVisitor(Block block){
        blockStatements = new ArrayList<>();
        block.accept(this);
    }

    // region <Statement>
    //Statement:
    //      AssertStatement,
    //      Block,
    //      BreakStatement,
    //      ConstructorInvocation,
    //      ContinueStatement,
    //      DoStatement,
    //      EmptyStatement,
    //      EnhancedForStatement
    //      ExpressionStatement,
    //      ForStatement,
    //      IfStatement,
    //      LabeledStatement,
    //      ReturnStatement,
    //      SuperConstructorInvocation,
    //      SwitchCase,
    //      SwitchStatement,
    //      SynchronizedStatement,
    //      ThrowStatement,
    //      TryStatement,
    //      TypeDeclarationStatement,
    //      VariableDeclarationStatement,
    //      WhileStatement
    // endregion <Statement>


    @Override
    public boolean visit(AssertStatement node) {
        // region <grammar>
        /**
         * AssertStatement:
         *      assert Expression [ : Expression ] ;
         */
        // endregion <grammar>
        blockStatements.add(node);
        return false;
    }

    @Override
    public boolean visit(Block node) {
        // region <grammar>
        /**
         * Block:
         *      { { Statement } }
         */
        // endregion <grammar>

        List<Statement> statements = node.statements();
        for(Statement statement : statements){
            statement.accept(this);
        }

        return false;
    }

    @Override
    public boolean visit(BreakStatement node) {
        // region <grammar>
        /**
         *  BreakStatement:
         *      break [ Identifier ] ;
         */
        // endregion <grammar>
        blockStatements.add(node);
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
        blockStatements.add(node);
        return false;
    }

    @Override
    public boolean visit(ContinueStatement node) {
        // region <grammar>
        /**
         *  ContinueStatement:
         *      continue [ Identifier ] ;
         */
        // endregion <grammar>
        blockStatements.add(node);
        return false;
    }

    @Override
    public boolean visit(DoStatement node) {
        // region <grammar>
        /**
         *  DoStatement:
         *      do Statement while ( Expression ) ;
         */
        // endregion <grammar>

        int formerSize = blockStatements.size();
        Statement statement = node.getBody();
        statement.accept(this);

        blockStatements.add(node);

        int latterSize = blockStatements.size();
        node.setProperty("size" , latterSize - formerSize);
        node.setProperty("start" , formerSize);
        node.setProperty("end" , latterSize - 1);

        return false;
    }

    @Override
    public boolean visit(EmptyStatement node) {
        // region <grammar>
        /**
         *  EmptyStatement:
         *      ;
         */
        // endregion <grammar>

        return false;
    }

    @Override
    public boolean visit(EnhancedForStatement node) {
        // region <grammar>
        /**
         *  EnhancedForStatement:
         *      for ( FormalParameter : Expression )
         *          Statement
         */
        // endregion <grammar>
        int formerSize = blockStatements.size();

        Statement statement = node.getBody();
        blockStatements.add(node);

        statement.accept(this);

        int latterSize = blockStatements.size();

        node.setProperty("size" , latterSize - formerSize); // 该语句块一共几句
        node.setProperty("start" , formerSize); // 该语句块从第几行开始，对应开始语句的下标
        node.setProperty("end" , latterSize - 1); // 该语句块在第几行结束，对应结束语句的下标
        return false;
    }

    @Override
    public boolean visit(ExpressionStatement node) {
        // region <grammar>
        /**
         *  ExpressionStatement:
         *      StatementExpression ;
         */
        // endregion <grammar>
        blockStatements.add(node);
        return false;
    }

    @Override
    public boolean visit(ForStatement node) {
        // region <grammar>
        /**
         *
         */
        // endregion <grammar>

        int formerSize = blockStatements.size();
        int latterSize = 0;

        Statement statement = node.getBody();

        blockStatements.add(node);

        statement.accept(this);

        latterSize = blockStatements.size();
        node.setProperty("size" , latterSize - formerSize);
        node.setProperty("start" , formerSize);
        node.setProperty("end" , latterSize - 1);

        return false;
    }

    @Override
    public boolean visit(IfStatement node) {
        // region <grammar>
        /**
         *  IfStatement:
         *      if ( Expression ) Statement [ else Statement]
         */
        // endregion <grammar>

        int formerSize = blockStatements.size();
        int latterSize;

        Expression condition = node.getExpression();
        Statement thenStatement = node.getThenStatement();
        Statement elseStatement = node.getElseStatement();

        blockStatements.add(node);
        thenStatement.accept(this);
        latterSize = blockStatements.size();

        node.setProperty("size" , latterSize - formerSize);
        node.setProperty("start" , formerSize);
        node.setProperty("end" , latterSize - 1);

        if(elseStatement != null){
            ASTParser astParser = ASTParser.newParser(AST.JLS8);
            astParser.setKind(ASTParser.K_STATEMENTS);
            astParser.setSource(("if(!(" + condition.toString() + "));").toCharArray());
            IfStatement ifStatement = (IfStatement) (
                    (Block)(astParser.createAST(null))
                    ).statements().get(0);

            formerSize = blockStatements.size();

            blockStatements.add(ifStatement);

            elseStatement.accept(this);
            latterSize = blockStatements.size();

            ifStatement.setProperty("size" , latterSize - formerSize);
            ifStatement.setProperty("start" , formerSize);
            ifStatement.setProperty("end" , latterSize - 1);
        }

        return false;
    }

    @Override
    public boolean visit(LabeledStatement node) {
        // region <grammar>
        /**
         *  LabeledStatement:
         *      Identifier : Statement
         */
        // endregion <grammar>

        blockStatements.add(node);
        return false;
    }

    @Override
    public boolean visit(ReturnStatement node) {
        // region <grammar>
        /**
         *  ReturnStatement:
         *      return [ Expression ] ;
         */
        // endregion <grammar>

        blockStatements.add(node);
        return false;
    }

    @Override
    public boolean visit(SuperConstructorInvocation node) {
        // region <grammar>
        /**
         *  SuperConstructorInvocation:
         *      [ Expression . ]
         *      [ < Type { , Type } > ]
         *          super ( [ Expression { , Expression } ] ) ;
         */
        // endregion <grammar>

        blockStatements.add(node);
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

        int switchFormerSize = blockStatements.size();
        int formerSize = 0;
        int latterSize = 0;

        List<Statement> statements = node.statements();
        blockStatements.add(node);
        int statementSize = statements.size();
        for(int i = 0 ; i < statementSize ; i ++){
            Statement temp = statements.get(i);
            if(temp instanceof SwitchCase){
                formerSize = blockStatements.size();
                blockStatements.add(temp);

                Statement statement = ++ i < statementSize ? statements.get(i) : null;
                if(statement != null){
                    statement.accept(this);
                }
                latterSize = blockStatements.size();

                temp.setProperty("size" , latterSize - formerSize);
                temp.setProperty("start" , formerSize);
                temp.setProperty("end" , latterSize - 1);
            }else{
                blockStatements.add(temp);
            }
        }
        latterSize = blockStatements.size();

        node.setProperty("size" , latterSize - switchFormerSize) ;
        node.setProperty("start" , switchFormerSize);
        node.setProperty("end" , latterSize - 1);

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

        int formerSize = blockStatements.size();
        int latterSize = 0;


        Block block = node.getBody();

        blockStatements.add(node);

        block.accept(this);

        latterSize = blockStatements.size();
        node.setProperty("size" , latterSize - formerSize);
        node.setProperty("start" , formerSize);
        node.setProperty("end" , latterSize - 1);
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

        blockStatements.add(node);
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

        int formerSize = blockStatements.size();
        int latterSize = 0;

        Block tryBlock = node.getBody();
        List<CatchClause> catchClauses = node.catchClauses();
        Block finallyBlock = node.getFinally();

        blockStatements.add(node);

        tryBlock.accept(this);

        latterSize = blockStatements.size();
        node.setProperty("size" , latterSize - formerSize);
        node.setProperty("start" , formerSize);
        node.setProperty("end" , latterSize - 1);

        for(CatchClause catchClause : catchClauses){
            // region <grammar>
            /**
             *  CatchClause:
             *      catch ( FormalParameter ) Block
             */
            // endregion <grammar>
            formerSize = blockStatements.size();

            blockStatements.add(catchClause);

            Block catchBlock = catchClause.getBody();
            catchBlock.accept(this);

            latterSize = blockStatements.size();
            catchBlock.setProperty("size" , latterSize - formerSize);
            catchBlock.setProperty("start" , formerSize);
            catchBlock.setProperty("end" , latterSize - 1);
        }

        formerSize = blockStatements.size();


        if(finallyBlock != null) {
            TryStatement tryStatement = AST.newAST(AST.JLS8).newTryStatement();
            blockStatements.add(tryStatement);

            finallyBlock.accept(this);

            latterSize = blockStatements.size();
            tryStatement.setProperty("size" , latterSize - formerSize);
            tryStatement.setProperty("start" , formerSize);
            tryStatement.setProperty("end" , latterSize - 1);
        }
        return false;
    }

    @Override
    public boolean visit(TypeDeclarationStatement node) {
        // region <grammar>
        /**
         *
         */
        // endregion <grammar>

        return false;
    }

    @Override
    public boolean visit(VariableDeclarationStatement node) {
        // region <grammar>
        /**
         *  VariableDeclarationStatement:
         *      { ExtendedModifier } Type VariableDeclarationFragment
         *      { , VariableDeclarationFragment } ;
         */
        // endregion <grammar>

        blockStatements.add(node);
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
        int formerSize = blockStatements.size();
        int latterSize = 0;

        blockStatements.add(node);

        Statement statement = node.getBody();
        statement.accept(this);

        latterSize = blockStatements.size();

        node.setProperty("size" , latterSize - formerSize);
        node.setProperty("start" , formerSize);
        node.setProperty("end" , latterSize - 1);
        return false;
    }

}

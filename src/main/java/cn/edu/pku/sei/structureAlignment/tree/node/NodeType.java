package cn.edu.pku.sei.structureAlignment.tree.node;

/**
 * Created by oliver on 2017/12/23.
 */
public enum NodeType {
    NLTEXT_V,
    CODE_AnnotationTypeDeclaration,
    CODE_AnnotationTypeMemberDeclaration,
    CODE_AnonymousClassDeclaration,
    CODE_ArrayAccess,
    CODE_ArrayCreation,
    CODE_ArrayInitializer,
    CODE_ArrayType,
    CODE_AssertStatement,
    CODE_Assignment,
    CODE_Block,
    CODE_BlockComment,
    CODE_BooleanLiteral, // true | false
    CODE_BreakStatement,
    CODE_CastExpression,
    CODE_CatchClause,
    CODE_CharacterLiteral,
    CODE_ClassInstanceCreation,
    CODE_CompilationUnit,
    CODE_ConditionalExpression,
    CODE_ConstructorInvocation,
    CODE_ContinueStatement,
    CODE_CreationReference,
    CODE_Dimension,
    CODE_DoStatement,
    CODE_EmptyStatement,
    CODE_EnhancedForStatement,
    CODE_EnumConstantDeclaration,
    CODE_EnumDeclaration,
    CODE_ExpressionMethodReference,
    CODE_ExpressionStatement,
    CODE_FieldAccess,
    CODE_FieldDeclaration,
    CODE_ForStatement,
    CODE_IfStatement,
    CODE_ImportDeclaration,
    CODE_InfixExpression,
    CODE_Initializer,
    CODE_InstanceofExpression,
    CODE_IntersectionType,
    CODE_Javadoc,
    CODE_LabeledStatement,
    CODE_LambdaExpression,
    CODE_LineComment,
    CODE_MarkerAnnotation,
    CODE_MemberRef,
    CODE_MemberValuePair,
    CODE_MethodRef,
    CODE_MethodRefParameter,
    CODE_MethodDeclaration,
    CODE_MethodInvocation,
    CODE_Modifier,  //public | protected | private | static | abstract | final | native | synchronized | transient | volatile | strictfp | default
    CODE_NameQualifiedType,
    CODE_NormalAnnotation,
    CODE_NullLiteral,
    CODE_NumberLiteral,
    CODE_PackageDeclaration,
    CODE_ParameterizedType,
    CODE_ParenthesizedExpression,
    CODE_PostfixExpression,
    CODE_PostfixExpression_Operator,
    CODE_PrefixExpression,
    CODE_PrefixExpression_Operator,
    CODE_PrimitiveType,
    CODE_QualifiedName,
    CODE_QualifiedType,
    CODE_ReturnStatement,
    CODE_SimpleName,
    CODE_SimpleType,
    CODE_SingleMemberAnnotation,
    CODE_SingleVariableDeclaration,
    CODE_StringLiteral,
    CODE_SuperConstructorInvocation,
    CODE_SuperFieldAccess,
    CODE_SuperMethodInvocation,
    CODE_SuperMethodReference,
    CODE_SwitchCase,
    CODE_SwitchStatement,
    CODE_SynchronizedStatement,
    CODE_TagElement,
    CODE_TextElement,
    CODE_ThisExpression,
    CODE_ThrowStatement,
    CODE_TryStatement,
    CODE_TypeDeclaration,
    CODE_TypeDeclarationStatement,
    CODE_TypeLiteral,
    CODE_TypeMethodReference,
    CODE_TypeParameter,
    CODE_UnionType,
    CODE_VariableDeclarationExpression,
    CODE_VariableDeclarationStatement,
    CODE_VariableDeclarationFragment,
    CODE_WhileStatement,
    CODE_WildcardType,

    // all the type below are used to indicate the nodes are left node.

    CODE_Identifier,

    CODE_InfixExpression_OPERATOR_TIMES,        // *
    CODE_InfixExpression_OPERATOR_DIVIDE,       // /
    CODE_InfixExpression_OPERATOR_REMAINDER,    // %
    CODE_InfixExpression_OPERATOR_PLUS,         // +
    CODE_InfixExpression_OPERATOR_MINUS,        // -
    CODE_InfixExpression_OPERATOR_LEFT_SHIFT,   // <<
    CODE_InfixExpression_OPERATOR_RIGHT_SHIFT,  // >>
    CODE_InfixExpression_OPERATOR_RIGHT_SHIFT_UNSIGNED,     // >>>
    CODE_InfixExpression_OPERATOR_LESS,         // <
    CODE_InfixExpression_OPERATOR_GREATER,      // >
    CODE_InfixExpression_OPERATOR_LESS_EQUALS,  // <=
    CODE_InfixExpression_OPERATOR_GREATER_EQUALS,   // >=
    CODE_InfixExpression_OPERATOR_EQUALS,       // ==
    CODE_InfixExpression_OPERATOR_NOT_EQUALS,   // !=
    CODE_InfixExpression_OPERATOR_XOR,           // ^
    CODE_InfixExpression_OPERATOR_AND,           // &
    CODE_InfixExpression_OPERATOR_OR,            // |
    CODE_InfixExpression_OPERATOR_CONDITIONAL_AND,           // &&
    CODE_InfixExpression_OPERATOR_CONDITIONAL_OR,           // ||

    CODE_ASSIGNMENT_OPERATOR,
    CODE_ASSIGNMENT_OPERATOR_ASSIGN,    // =
    CODE_ASSIGNMENT_OPERATOR_PLUS_ASSIGN,   // +=
    CODE_ASSIGNMENT_OPERATOR_MINUS_ASSIGN,  // -=
    CODE_ASSIGNMENT_OPERATOR_TIMES_ASSIGN,  //*=
    CODE_ASSIGNMENT_OPERATOR_DIVIDE_ASSIGN,     // /=
    CODE_ASSIGNMENT_OPERATOR_BIT_AND_ASSIGN,    // &=
    CODE_ASSIGNMENT_OPERATOR_BIT_OR_ASSIGN,     // |=
    CODE_ASSIGNMENT_OPERATOR_BIT_XOR_ASSIGN,    // ^=
    CODE_ASSIGNMENT_OPERATOR_REMAINDER_ASSIGN,      // %=
    CODE_ASSIGNMENT_OPERATOR_LEFT_SHIFT_ASSIGN,     // <<=
    CODE_ASSIGNMENT_OPERATOR_RIGHT_SHIFT_SIGNED_ASSIGN,     // >>=
    CODE_ASSIGNMENT_OPERATOR_RIGHT_SHIFT_UNSIGNED_ASSIGN,   // >>>=


    ADDED_METHOD_NAME,  //
    ADDED_KEYWORD,
    ADDED_CHAR_LEFT_PARENTHESIS,    // (
    ADDED_CHAR_RIGHT_PARENTHESIS,   // )
    ADDED_CHAR_LEFT_BRACKET,        // [
    ADDED_CHAR_RIGHT_BRACKET,       // ]
    ADDED_CHAR_LEFT_BRACE,          // {
    ADDED_CHAR_RIGHT_BRACE,         // }
    ADDED_CHAR_LEFT_ANGLE_BRACKET,  // <
    ADDED_CHAR_RIGHT_ANGLE_BRACKET, // >
    ADDED_CHAR_COLON,               // :
    ADDED_CHAR_DOUBLE_COLON,        // ::
    ADDED_CHAR_COMMA,                // ,
    ADDED_CHAR_SEMICOLON,             // ;
    ADDED_CHAR_DOT,                 // .
    ADDED_CHAR_QUESTION,            //?
    ADDED_CHAR_AT,                   // @
    ADDED_CHAR_ELLIPSIS,             // ...


    TEXT_R,

    //Clause Level
    TEXT_S , //simple declarative clause, i.e. one that is not introduced by a (possible empty) subordinating conjunction or a wh-word and that does not exhibit subject-verb inversion.
    TEXT_SBAR , //Clause introduced by a (possibly empty) subordinating conjunction.
    TEXT_SBARQ , //Direct question introduced by a wh-word or a wh-phrase. Indirect questions and relative clauses should be bracketed as SBAR, not SBARQ.
    TEXT_SINV , //Inverted declarative sentence, i.e. one in which the subject follows the tensed verb or modal.
    TEXT_SQ  ,  //Inverted yes/no question, or main clause of a wh-question, following the wh-phrase in SBARQ.

    //Phrase Level
    TEXT_ADJP , //Adjective Phrase.
    TEXT_ADVP , //Adverb Phrase.
    TEXT_CONJP , //Conjunction Phrase.
    TEXT_FRAG , //Fragment.
    TEXT_INTJ , //Interjection. Corresponds approximately to the part-of-speech tag UH.
    TEXT_LST , //List marker. Includes surrounding punctuation.
    TEXT_NAC , // Not a Constituent; used to show the scope of certain prenominal modifiers within an NP.
    TEXT_NP , //Noun Phrase.
    TEXT_NX , //Used within certain complex NPs to mark the head of the NP. Corresponds very roughly to N-bar level but used quite differently.
    TEXT_PP , //Prepositional Phrase.
    TEXT_PRN , //Parenthetical.
    TEXT_PRT , //Particle. Category for words that should be tagged RP.
    TEXT_QP , //Quantifier Phrase (i.e. complex measure/amount phrase); used within NP.
    TEXT_RRC , //Reduced Relative Clause.
    TEXT_UCP , //Unlike Coordinated Phrase.
    TEXT_VP , //Vereb Phrase.
    TEXT_WHADJP , //Wh-adjective Phrase. Adjectival phrase containing a wh-adverb, as in how hot.
    TEXT_WHAVP , //Wh-adverb Phrase. Introduces a clause with an NP gap. May be null (containing the 0 complementizer) or lexical, containing a wh-adverb such as how or why.
    TEXT_WHNP , //Wh-noun Phrase. Introduces a clause with an NP gap. May be null (containing the 0 complementizer) or lexical, containing some wh-word, e.g. who, which book, whose daughter, none of which, or how many leopards.
    TEXT_WHPP , //Wh-prepositional Phrase. Prepositional phrase containing a wh-noun phrase (such as of which or by whose authority) that either introduces a PP gap or is contained by a WHNP.
    TEXT_X , //Unknown, uncertain, or unbracketable. X is often used for bracketing typos and in bracketing the...the-constructions.

    //Word level
    TEXT_CD , //Cardinal number
    TEXT_DT , //Determiner
    TEXT_EX , //Existential there
    TEXT_FW , //Foreign word
    TEXT_IN , //Preposition or subordinating conjunction
    TEXT_JJ , //Adjective
    TEXT_JJR , //Adjective, comparative
    TEXT_JJS , //Adjective, superlative
    TEXT_LS , //List item marker
    TEXT_MD , //Modal
    TEXT_NN , //Noun, singular or mass
    TEXT_NNS , //Noun, plural
    TEXT_NNP , //Proper noun, singular
    TEXT_NNPS , //Proper noun, plural
    TEXT_PDT , //Predeterminer
    TEXT_POS , //Possessive ending
    TEXT_PRP , //Personal pronoun
    TEXT_PRP$ , //Possessive pronoun (prolog version PRP-S)
    TEXT_RB , //Adverb
    TEXT_RBR , //Adverb, comparative
    TEXT_RBS , //Adverb, superlative
    TEXT_RP , //Particle
    TEXT_SYM , //Symbol
    TEXT_TO , //to
    TEXT_UH , //Interjection
    TEXT_VB , //Verb, base form
    TEXT_VBD , //Verb, past tense
    TEXT_VBG , //Verb, gerund or present participle
    TEXT_VBN , //Verb, past participle
    TEXT_VBP , //Verb, non-3rd person singular present
    TEXT_VBZ , //Verb, 3rd person singular present
    TEXT_WDT , //Wh-determiner
    TEXT_WP , //Wh-pronoun
    TEXT_WP$ , // Possessive wh-pronoun (prolog version WP-S)
    TEXT_WRB , //Wh-adverb
    NULL
}

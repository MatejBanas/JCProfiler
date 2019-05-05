package opencryptoutils;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntryStmt;
import com.github.javaparser.ast.visitor.GenericListVisitorAdapter;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.nodeTypes.NodeWithStatements;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * @author Matej Banas
 */
public final class Parser {
    private static ArrayList<Byte> constants = new ArrayList<>();
    
    // Noninstantiable utility class
    private Parser(){
        throw new AssertionError();
    }
    
    public static CompilationUnit parseFile(String filepath) throws IOException{
        File file = new File(filepath);
        CompilationUnit compilationUnit = JavaParser.parse(file);
        return compilationUnit;
    }
 
    public static void setConstants(ArrayList<Byte> constants) {
        Parser.constants = constants;
    }

    public static ArrayList<Byte> getConstants() {
        return constants;
    }
    
    /**
     * finds all PERFRAP comments and changes them to code  
     * 
     * @param compilationUnit compilation unit
     */
    public static void commentToCode(CompilationUnit compilationUnit) {   
        compilationUnit.findAll(Statement.class).stream()
                .filter(c -> c.getComment().isPresent() && c.getComment().get().getContent().contains("PERFTRAP"))
                .forEach( c -> {
                    c.getParentNode()                           
                            .map(parent -> (NodeWithStatements) parent)
                            .map(NodeWithStatements::getStatements)
                            .ifPresent(statements -> { 
                                statements.addBefore(JavaParser.parseStatement("PM.check(PMC.TRAP_methodName_0);"), c);
                                c.removeComment();                               
                                });       
                });
    }
    
    /**
     * finds all lines of code representing traps and changes them to comment
     * 
     * @param compilationUnit compilation unit
     */
    public static void codeToComment(CompilationUnit compilationUnit) {
       compilationUnit.accept(new CodeToCommentVisitor(), null);
    }
    
    /**
     * 
     * @param compilationUnit 
     */
    public static void addTrapsToCommentedMethod(CompilationUnit compilationUnit) {
        compilationUnit.accept(new AddTrapsToCommentedMethodsVisitor(), null);
    }
    
    /**
     * 
     * @param compilationUnit 
     */
    public static void removeTrapsFromCommentedMethod(CompilationUnit compilationUnit) {
        compilationUnit.accept(new RemoveTrapsFromCommentedMethodsVisitor(), null);
    }
    
    /**
     *
     * @param compilationUnit compilationUnit created by parsing source file
     */
    public static void changeApduTrigger(CompilationUnit compilationUnit) {
        //TODO wrong hex value?
        NodeList<Expression> values = new NodeList<>();
        NameExpr expr = compilationUnit.getTypes().get(0).getMember(2).asFieldDeclaration().getVariables().
                get(0).getInitializer().get().asArrayInitializerExpr().getValues().get(0).asNameExpr();
        values.add(expr);
        for (int i = 0; i < getConstants().size(); i++){
            values.add(new IntegerLiteralExpr(getConstants().get(i)));
        }
        compilationUnit.getType(0).getMember(2).asFieldDeclaration().getVariables().get(0).getInitializer().
                get().asArrayInitializerExpr().setValues(values);
    }

    /**
     * triggers a visitor that finds all constants and adds them to class attribute List constants
     * they can be used later for apdu triggers
     * visitor used: GetConstantsVisitor
     * ?
     *
     * @param compilationUnit compilationUnit created by parsing source file
     */
    public static void getSourceConstants(CompilationUnit compilationUnit) {
        ArrayList<Byte> tmpConstants = new ArrayList<>();
        compilationUnit.findAll(VariableDeclarator.class).stream()
                .filter(f -> f.getType().isPrimitiveType())
                .forEach(f -> tmpConstants.addAll(f.accept(new GetConstantsVisitor(), null)));
        setConstants(tmpConstants);
    }

    /**
     * triggers a visitor that adds case statement from PM.java file to switch statement in source file
     * visitor used: AddSwitchCaseStmntVisitor
     *
     * @param compilationUnit compilationUnit created by parsing source file
     */
    public static void insertSwitchCaseStmnt(CompilationUnit compilationUnit) {
        compilationUnit.accept(new AddSwitchCaseStmntVisitor(), null);
        insertStopConstant(compilationUnit);
    }

    /**
     * adds INS_PERF_SETSTOP constant from PM to source class
     *
     * @param compilationUnit compilationUnit created by parsing source file
     */
    public static void insertStopConstant(CompilationUnit compilationUnit) { 
        byte x = (byte) 0xf5;
        while (getConstants().contains(x)) {
            x = (byte) (x + 0x01);
        }
        BodyDeclaration<?> declaration = JavaParser.parseAnnotationBodyDeclaration("public final static byte INS_PERF_SETSTOP = (byte) " + x +";");
        compilationUnit.getTypes().get(0).getMembers().addFirst(declaration);
    }

    /**
     * writes changes to source file
     *
     * @param path            path to source file
     * @param compilationUnit compilationUnit created by parsing source file
     * @throws IOException exception
     */
    public static void writeChanges(String path, CompilationUnit compilationUnit) throws IOException {
        FileWriter fw = new FileWriter(path);
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(compilationUnit.toString());
        bw.close();
        fw.close();
    }

    /**
     * changes package declaration of compilation unit b to package declaration from CU a
     *
     * @param a compilationUnit created by parsing source file
     * @param b compilationUnit created by parsing file of which we want to change the package declaration
     */
    public static void changePackageDeclaration(CompilationUnit a, CompilationUnit b) {
        try {
            if (a.getPackageDeclaration().isPresent()) {
                b.setPackageDeclaration(a.getPackageDeclaration().get());
            } else {
                String x = null;
                b.setPackageDeclaration(x);
            }
            
        } catch (NullPointerException ex){
            System.err.println("compilation unit is null " + ex.getMessage());
        } finally {
        }
    }


    /**
     * visitor that gets all constants
     */
    private static class GetConstantsVisitor extends GenericListVisitorAdapter<Byte, Void> {
        List<Byte> bytes = new ArrayList<>();

        @Override
        public List<Byte> visit(VariableDeclarator variableDeclarator, Void arg) {
            String name = variableDeclarator.getName().asString();
            if (variableDeclarator.getType().asString().equals("byte") && name.equals(name.toUpperCase())) {
                bytes.add((byte) variableDeclarator.getInitializer().get().asCastExpr().getExpression().asIntegerLiteralExpr().asInt());
                //variableDeclarator.getInitializer().get().ifIntegerLiteralExpr(f -> bytes.add((byte) f.asInt()));
            }
            return bytes;
        }
    }

    
    /**
     * visitor that adds switch case statement
     */
    private static class AddSwitchCaseStmntVisitor extends ModifierVisitor<Void> {
        @Override
        public MethodDeclaration visit(MethodDeclaration methodDeclaration, Void arg) {
            super.visit(methodDeclaration, arg);
            if (methodDeclaration.getName().asString().equals("process")) {
                NodeList<Statement> statements = methodDeclaration.getBody().get().getStatements();
                BlockStmt body = new BlockStmt();
                NodeList<Statement> toBeInserted = new NodeList<>();

                for (int i = 0; i < statements.size(); i++) {
                    if (statements.get(i).isSwitchStmt()) {
                        String name = statements.get(i).asSwitchStmt().getSelector().asArrayAccessExpr().getName().toString();
                        toBeInserted.add(JavaParser.parseStatement("PM.m_perfStop = Util.makeShort(" + name + "[ISO7816.OFFSET_CDATA], " + name + "[(short) (ISO7816.OFFSET_CDATA + 1)]);"));
                        toBeInserted.add(new BreakStmt().removeLabel());
                        NodeList<SwitchEntryStmt> entryStmts = statements.get(i).asSwitchStmt().getEntries();
                        entryStmts.addBefore(new SwitchEntryStmt(new NameExpr("INS_PERF_SETSTOP"), toBeInserted), statements.get(i).asSwitchStmt().getEntry(entryStmts.size() - 1));
                        statements.get(i).asSwitchStmt().setEntries(entryStmts);
                    }
                    body.addStatement(statements.get(i));
                }
                methodDeclaration.setBody(body);
            }
            return methodDeclaration;
        }
    }
    
    /**
     * 
     */
    private static class AddTrapsToCommentedMethodsVisitor extends ModifierVisitor<Void> {
        @Override
        public MethodDeclaration visit(MethodDeclaration methodDeclaration, Void arg) {
            super.visit(methodDeclaration, arg);
            if (methodDeclaration.getComment().isPresent() && methodDeclaration.getComment().get().getContent().contains("ADD TRAPS TO THIS METHOD")) {
                NodeList<Statement> statements = methodDeclaration.getBody().get().getStatements();
                for (int i = 0; i < statements.size(); i++) {
                    if (statements.get(i).getComment().isPresent() && !statements.get(i).getComment().get().getContent().contains("PERFTRAP")) {
                        statements.get(i).addOrphanComment(statements.get(i).getComment().get());
                        methodDeclaration.getBody().get().addOrphanComment(statements.get(i).getOrphanComments().get(0));
                    }
                    statements.get(i).setLineComment("PERFTRAP");       
                }
                if (!statements.get(statements.size()-1).isReturnStmt()) {
                    methodDeclaration.getBody().get().addOrphanComment(new LineComment("PERFTRAP"));
                }
            }
            return methodDeclaration;
        }
    }
    
    /**
     * 
     */
    private static class RemoveTrapsFromCommentedMethodsVisitor extends ModifierVisitor<Void> {
        @Override
        public MethodDeclaration visit(MethodDeclaration methodDeclaration, Void arg) {
            super.visit(methodDeclaration, arg);
            if(methodDeclaration.getComment().isPresent() && methodDeclaration.getComment().get().getContent().contains("ADD TRAPS TO THIS METHOD")) {
                methodDeclaration.getAllContainedComments().stream()
                        .filter(f -> f.getContent().contains("PERFTRAP"))
                        .forEach(f -> f.remove());
            }
            return methodDeclaration;
        }
    }
    
    private static class CodeToCommentVisitor extends ModifierVisitor<Void> {
        @Override
        public MethodDeclaration visit(MethodDeclaration methodDeclaration, Void arg) {
            //super.visit(methodDeclaration, arg);
            methodDeclaration.findAll(Statement.class).stream()
                    .filter(c -> c.isExpressionStmt() &&
                            c.asExpressionStmt().getExpression().isMethodCallExpr() &&
                            c.asExpressionStmt().getExpression().asMethodCallExpr().getScope().isPresent() &&
                            c.asExpressionStmt().getExpression().asMethodCallExpr().getScope().get().toString().equals("PM"))
                    .forEach(c -> {
                        Node parent = c.getParentNode().get();
                        List<Node> children = parent.getChildNodes();

                        if(children.size() != children.indexOf(c) + 1) {
                            children.get(children.indexOf(c) + 1).setLineComment("PERFTRAP");
                        } else {                            
                            parent.addOrphanComment(new LineComment("PERFTRAP"));
                        }
                        parent.remove(c);
                    });        
            return methodDeclaration;
        }
    }
}
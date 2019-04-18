package opencryptoutils;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
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
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * @author Matej Banas
 */
public class Parser {
    private static ArrayList<Byte> constants = new ArrayList<>();
    private static ArrayList<String> methods = new ArrayList<>();
    
    
    public static void main(String[] args) throws Exception {
        //TODO check if traps are reached or not, wrong constants or logicaly, if stmnt
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

    public static ArrayList<String> getMethods() {
        return methods;
    }

    public static void setMethods(ArrayList<String> methods) {
        Parser.methods = methods;
    }
     
    
    public static void addTrapsToCommentedMethod(CompilationUnit compilationUnit) {
        compilationUnit.accept(new AddTrapsToCommentedMethodsVisitor(), null);
    }
    
    
    public static void removeTrapsFromCommentedMethod(CompilationUnit compilationUnit) {
        compilationUnit.accept(new RemoveTrapsFromCommentedMethodsVisitor(), null);
    }
    
    /**
     * 
     * @param compilationUnit 
     */
    public static void getMethodDeclarations(CompilationUnit compilationUnit) {
        //ArrayList<String> methodsList = new ArrayList<>();
        compilationUnit.accept(new getMethodDecl(), getMethods());
        //getMethods().forEach(n -> System.out.println("name: " + n));
        
    }
    /**
     * 
     * @param compilationUnit 
     */
    public static void addTrapsToMethods(CompilationUnit compilationUnit) {
        compilationUnit.accept(new AddTrapsAtMethods(), null);   
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
     * triggers a visitor that adds traps to the beginning and end of every method in source file
     * visitor used: AddTrapsVisitor
     *
     * @param compilationUnit compilationUnit created by parsing source file
     */
    public static void insertTrapsToEveryMethod(CompilationUnit compilationUnit) {
        compilationUnit.accept(new AddTrapsVisitor(), null);
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
        /*
        if (getConstants().contains(0xf5)) {
            TODO
        }
        */
        BodyDeclaration<?> declaration = JavaParser.parseAnnotationBodyDeclaration("public final static byte INS_PERF_SETSTOP = (byte) 0xf5;");
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
     * visitor that adds traps
     */
    private static class AddTrapsVisitor extends ModifierVisitor<Void> {
        @Override
        public MethodDeclaration visit(MethodDeclaration methodDeclaration, Void arg) {
            super.visit(methodDeclaration, arg);
            if (!methodDeclaration.getName().asString().matches("process") && !methodDeclaration.getName().asString().matches("install")) {
                boolean returnFound = false;
                NodeList<Statement> statements = methodDeclaration.getBody().get().getStatements();
                BlockStmt body = new BlockStmt();
                body.addStatement("PM.check(PMC.TRAP_methodName_0);");
                for (int i = 0; i < statements.size(); i++) {
                    if (statements.get(i).isReturnStmt()) {
                        returnFound = true;
                        body.addStatement("PM.check(PMC.TRAP_methodName_0);");
                    }
                    body.addStatement(statements.get(i));
                }
                if (!returnFound) {
                    body.addStatement("PM.check(PMC.TRAP_methodName_0);");
                    //System.out.println(body.getStatement(body.getStatements().size()).getClass());
                }
                methodDeclaration.setBody(body);
            }
            return methodDeclaration;
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
                NodeList<Statement> toBeInserted = new NodeList<Statement>();

                for (int i = 0; i < statements.size(); i++) {
                    /**
                     if(statements.get(i).getClass().equals(ExpressionStmt.class)){
                     System.out.println(statements.get(i).asExpressionStmt().getExpression().getClass());
                     }
                     */
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
    private static class getMethodDecl extends VoidVisitorAdapter<List<String>> {
        @Override
        public void visit(MethodDeclaration methodDeclaration, List<String> collector){
            super.visit(methodDeclaration, collector);
            collector.add(methodDeclaration.getNameAsString());
        }
    }
    
    private static class AddTrapsAtMethods extends ModifierVisitor<Void> {
        @Override
        public MethodDeclaration visit(MethodDeclaration methodDeclaration, Void arg) {
            super.visit(methodDeclaration, arg);
            NodeList<Statement> statements = methodDeclaration.getBody().get().getStatements();
            BlockStmt body = new BlockStmt();
           
            for (Statement statement : statements) {               
                if (statement.isExpressionStmt() && statement.asExpressionStmt().getExpression().isMethodCallExpr()) {                   
                    if (getMethods().contains(statement.asExpressionStmt().getExpression().asMethodCallExpr().getName().asString())) {
                        body.addStatement("PM.check(PMC.TRAP_methodName_0);");
                        body.addStatement(statement);
                        body.addStatement("PM.check(PMC.TRAP_methodName_0);");
                    } 
                } else if (statement.isSwitchStmt()) {
                    for (SwitchEntryStmt switchEntry : statement.asSwitchStmt().getEntries()) {
                        NodeList<Statement> toBeInserted = new NodeList<>();
                        for (Statement statement2 : switchEntry.getStatements()) {
                            if (statement2.isExpressionStmt() && statement2.asExpressionStmt().getExpression().isMethodCallExpr()) {                   
                                if (getMethods().contains(statement2.asExpressionStmt().getExpression().asMethodCallExpr().getName().asString())) {
                                    toBeInserted.add(JavaParser.parseStatement("PM.check(PMC.TRAP_methodName_0);"));
                                    toBeInserted.add(statement2);
                                    toBeInserted.add(JavaParser.parseStatement("PM.check(PMC.TRAP_methodName_0);"));
                                } 
                            } else if (statement2.isExpressionStmt() && statement2.asExpressionStmt().getExpression().isAssignExpr()) {
                                if (statement2.asExpressionStmt().getExpression().asAssignExpr().getValue().isMethodCallExpr()) {
                                    if (getMethods().contains(statement2.asExpressionStmt().getExpression().asAssignExpr().getValue().asMethodCallExpr().getName().asString())) {
                                        toBeInserted.add(JavaParser.parseStatement("PM.check(PMC.TRAP_methodName_0);"));
                                        toBeInserted.add(statement2);
                                        toBeInserted.add(JavaParser.parseStatement("PM.check(PMC.TRAP_methodName_0);"));
                                    }
                                }
                            } else {
                                toBeInserted.add(statement2);
                            }      
                        }
                        switchEntry.setStatements(toBeInserted);
                    }
                    body.addStatement(statement);
                } else if (statement.isExpressionStmt() && statement.asExpressionStmt().getExpression().isAssignExpr()) {
                    if (statement.asExpressionStmt().getExpression().asAssignExpr().getValue().isMethodCallExpr()) {
                        if (getMethods().contains(statement.asExpressionStmt().getExpression().asAssignExpr().getValue().asMethodCallExpr().getName().asString())) {
                            body.addStatement("PM.check(PMC.TRAP_methodName_0);");
                            body.addStatement(statement);
                            body.addStatement("PM.check(PMC.TRAP_methodName_0);");
                        }
                    }
                    
                } else {
                    body.addStatement(statement);
                }
            }
            methodDeclaration.setBody(body);
            
            return methodDeclaration;
        }
    }
    
    private static class AddTrapsToCommentedMethodsVisitor extends ModifierVisitor<Void> {
        @Override
        public MethodDeclaration visit(MethodDeclaration methodDeclaration, Void arg) {
            super.visit(methodDeclaration, arg);
            if (methodDeclaration.getComment().isPresent() && methodDeclaration.getComment().get().getContent().contains("ADD TRAPS TO THIS METHOD")) {
                NodeList<Statement> statements = methodDeclaration.getBody().get().getStatements();
                BlockStmt body = new BlockStmt();
                for (int i = 0; i < statements.size(); i++) {
                    body.addStatement("PM.check(PMC.TRAP_methodName_0);");
                    body.addStatement(statements.get(i));
                }
                if (!statements.get(statements.size()-1).isReturnStmt()) {
                    body.addStatement("PM.check(PMC.TRAP_methodName_0);");
                }
                methodDeclaration.setBody(body);
            }
            return methodDeclaration;
        }
    }
    
    private static class RemoveTrapsFromCommentedMethodsVisitor extends ModifierVisitor<Void> {
        @Override
        public MethodDeclaration visit(MethodDeclaration methodDeclaration, Void arg) {
            super.visit(methodDeclaration, arg);
            if(methodDeclaration.getComment().isPresent() && methodDeclaration.getComment().get().getContent().contains("ADD TRAPS TO THIS METHOD")) {
                NodeList<Statement> statements = methodDeclaration.getBody().get().getStatements();
                BlockStmt body = new BlockStmt();
                for (int i = 0; i < statements.size(); i++) {
                    if (!statements.get(i).toString().contains("PM.check")) {
                        body.addStatement(statements.get(i));
                    }
                }
                methodDeclaration.setBody(body);
            }
            return methodDeclaration;
        }
    }
    
    
}
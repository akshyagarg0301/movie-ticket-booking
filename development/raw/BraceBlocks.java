import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** Wrap existing single-statement control-flow bodies without changing their contents. */
class BraceBlocks {
    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        for (String arg : args) {
            Path path = Path.of(arg);
            String source = Files.readString(path);
            var edits = new TreeMap<Integer, StringBuilder>(Comparator.reverseOrder());
            try (var manager = compiler.getStandardFileManager(null, null, null)) {
                var task = (JavacTask) compiler.getTask(null, manager, null,
                        List.of("-proc:none"), null, manager.getJavaFileObjects(path));
                for (var unit : task.parse()) {
                    var positions = Trees.instance(task).getSourcePositions();
                    new TreeScanner<Void, Void>() {
                        void wrap(StatementTree body) {
                            if (body == null || body instanceof BlockTree) return;
                            int start = (int) positions.getStartPosition(unit, body);
                            int end = (int) positions.getEndPosition(unit, body);
                            edits.computeIfAbsent(start, key -> new StringBuilder()).append("{\n");
                            edits.computeIfAbsent(end, key -> new StringBuilder()).append("\n}");
                        }
                        @Override public Void visitIf(IfTree node, Void unused) {
                            wrap(node.getThenStatement());
                            if (!(node.getElseStatement() instanceof IfTree)) wrap(node.getElseStatement());
                            return super.visitIf(node, unused);
                        }
                        @Override public Void visitForLoop(ForLoopTree node, Void unused) {
                            wrap(node.getStatement()); return super.visitForLoop(node, unused);
                        }
                        @Override public Void visitEnhancedForLoop(EnhancedForLoopTree node, Void unused) {
                            wrap(node.getStatement()); return super.visitEnhancedForLoop(node, unused);
                        }
                        @Override public Void visitWhileLoop(WhileLoopTree node, Void unused) {
                            wrap(node.getStatement()); return super.visitWhileLoop(node, unused);
                        }
                        @Override public Void visitDoWhileLoop(DoWhileLoopTree node, Void unused) {
                            wrap(node.getStatement()); return super.visitDoWhileLoop(node, unused);
                        }
                    }.scan(unit, null);
                }
            }
            var result = new StringBuilder(source);
            edits.forEach((position, insertion) -> result.insert(position, insertion));
            Files.writeString(path, result);
        }
    }
}

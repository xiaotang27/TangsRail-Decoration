package top.xiaotang27.trdm.fare.script;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 积木语句树的分支/移动/删除操作，以及 if 之后继续执行。 */
class ScriptTreeTest {

    private static ScriptNode add(FareScript script, String type) {
        final ScriptNode node = new ScriptNode(Scripts.uniqueId(script, "s"), type);
        script.nodes.add(node);
        return node;
    }

    private static ScriptNode number(FareScript script, double value) {
        final ScriptNode node = new ScriptNode(Scripts.uniqueId(script, "v"), "number");
        node.fields.put("value", Double.toString(value));
        script.nodes.add(node);
        return node;
    }

    private static ScriptNode setFare(FareScript script, double value) {
        final ScriptNode node = add(script, "set_fare");
        node.inputs.put("value", number(script, value).id);
        return node;
    }

    private static ScriptNode addFare(FareScript script, double value) {
        final ScriptNode node = add(script, "add_fare");
        node.inputs.put("value", number(script, value).id);
        return node;
    }

    private static ScriptNode ifNode(FareScript script, String op) {
        return ifNode(script, op, 2);
    }

    private static ScriptNode ifNode(FareScript script, String op, double right) {
        final ScriptNode node = add(script, "if");
        final ScriptNode compare = new ScriptNode(Scripts.uniqueId(script, "v"), "compare");
        compare.fields.put("op", op);
        script.nodes.add(compare);
        final ScriptNode intervals = new ScriptNode(Scripts.uniqueId(script, "v"), "context_intervals");
        script.nodes.add(intervals);
        compare.inputs.put("a", intervals.id);
        compare.inputs.put("b", number(script, right).id);
        node.inputs.put("condition", compare.id);
        return node;
    }

    private static double eval(FareScript script, long intervals) {
        final ScriptCompiler.Result result = ScriptCompiler.compile(script);
        assertTrue(result.errors().isEmpty(), () -> String.join("；", result.errors()));
        return result.compiled().run(new ScriptContext(intervals, 0, 0, 0));
    }

    @Test
    void nestedBranchesAndAfterCompile() {
        final FareScript script = Scripts.empty();
        final ScriptNode branch = ifNode(script, "<=");
        ScriptTree.append(script, ScriptTree.Slot.top(), branch.id);
        final ScriptNode thenSet = setFare(script, 2);
        final ScriptNode elseSet = setFare(script, 3);
        ScriptTree.append(script, new ScriptTree.Slot(branch.id, ScriptTree.Branch.THEN), thenSet.id);
        ScriptTree.append(script, new ScriptTree.Slot(branch.id, ScriptTree.Branch.ELSE), elseSet.id);
        final ScriptNode after = addFare(script, 1);
        ScriptTree.append(script, ScriptTree.Slot.top(), after.id);

        assertEquals(thenSet.id, ScriptTree.first(script,
                new ScriptTree.Slot(branch.id, ScriptTree.Branch.THEN)));
        assertEquals(elseSet.id, ScriptTree.first(script,
                new ScriptTree.Slot(branch.id, ScriptTree.Branch.ELSE)));
        assertEquals(after.id, ScriptTree.next(script, branch.id));
        assertEquals(4, ScriptTree.countStatements(script));
        assertEquals(3.0, eval(script, 1), 1e-9);
        assertEquals(4.0, eval(script, 5), 1e-9);
    }

    @Test
    void nestedIfExecutesAndContinues() {
        final FareScript script = Scripts.empty();
        final ScriptNode outer = ifNode(script, "<=", 5);
        ScriptTree.append(script, ScriptTree.Slot.top(), outer.id);

        final ScriptNode inner = ifNode(script, "<=", 2);
        ScriptTree.append(script, new ScriptTree.Slot(outer.id, ScriptTree.Branch.THEN), inner.id);
        final ScriptNode low = setFare(script, 2);
        final ScriptNode high = setFare(script, 3);
        ScriptTree.append(script, new ScriptTree.Slot(inner.id, ScriptTree.Branch.THEN), low.id);
        ScriptTree.append(script, new ScriptTree.Slot(inner.id, ScriptTree.Branch.ELSE), high.id);

        final ScriptNode after = addFare(script, 1);
        ScriptTree.append(script, ScriptTree.Slot.top(), after.id);

        assertEquals(3.0, eval(script, 1), 1e-9);
        assertEquals(4.0, eval(script, 4), 1e-9);
        assertEquals(1.0, eval(script, 9), 1e-9);
    }

    @Test
    void insertBeforeDetachAndBranchMove() {
        final FareScript script = Scripts.empty();
        final ScriptNode a = setFare(script, 1);
        final ScriptNode b = setFare(script, 2);
        final ScriptNode c = setFare(script, 3);
        ScriptTree.append(script, ScriptTree.Slot.top(), a.id);
        ScriptTree.append(script, ScriptTree.Slot.top(), b.id);
        ScriptTree.append(script, ScriptTree.Slot.top(), c.id);
        assertEquals(a.id, ScriptTree.first(script, ScriptTree.Slot.top()));

        assertTrue(ScriptTree.detach(script, b.id));
        assertNull(ScriptTree.next(script, b.id));
        assertEquals(c.id, ScriptTree.next(script, a.id));

        ScriptTree.insertBefore(script, ScriptTree.Slot.top(), b.id, c.id);
        assertEquals(b.id, ScriptTree.next(script, a.id));
        assertEquals(c.id, ScriptTree.next(script, b.id));

        ScriptTree.detach(script, b.id);
        ScriptTree.insertBefore(script, ScriptTree.Slot.top(), b.id, a.id);
        assertEquals(b.id, ScriptTree.first(script, ScriptTree.Slot.top()));
        assertEquals(a.id, ScriptTree.next(script, b.id));

        final ScriptNode branch = ifNode(script, "<");
        ScriptTree.append(script, ScriptTree.Slot.top(), branch.id);
        ScriptTree.detach(script, a.id);
        ScriptTree.append(script, new ScriptTree.Slot(branch.id, ScriptTree.Branch.THEN), a.id);
        assertEquals(a.id, ScriptTree.first(script,
                new ScriptTree.Slot(branch.id, ScriptTree.Branch.THEN)));
        assertTrue(ScriptTree.isDescendant(script, a.id, branch.id));

        ScriptTree.detach(script, a.id);
        assertNull(ScriptTree.first(script, new ScriptTree.Slot(branch.id, ScriptTree.Branch.THEN)));
        assertFalse(ScriptTree.isDescendant(script, a.id, branch.id));
    }

    @Test
    void collectPruneAndValueOwner() {
        final FareScript script = Scripts.empty();
        final ScriptNode branch = ifNode(script, "<=");
        ScriptTree.append(script, ScriptTree.Slot.top(), branch.id);
        final ScriptNode thenSet = setFare(script, 2);
        final ScriptNode elseSet = setFare(script, 3);
        ScriptTree.append(script, new ScriptTree.Slot(branch.id, ScriptTree.Branch.THEN), thenSet.id);
        ScriptTree.append(script, new ScriptTree.Slot(branch.id, ScriptTree.Branch.ELSE), elseSet.id);
        final ScriptNode after = addFare(script, 1);
        ScriptTree.append(script, ScriptTree.Slot.top(), after.id);
        final ScriptNode orphan = number(script, 9);

        final Set<String> subtree = new HashSet<>();
        ScriptTree.collectSubtree(script, branch.id, subtree);
        assertTrue(subtree.contains(branch.id));
        assertTrue(subtree.contains(thenSet.id));
        assertTrue(subtree.contains(elseSet.id));
        assertTrue(subtree.contains(thenSet.inputs.get("value")));
        assertFalse(subtree.contains(after.id));

        assertEquals(thenSet.id, ScriptTree.containingStatement(script,
                thenSet.inputs.get("value")));
        final ScriptTree.ValueOwner owner = ScriptTree.ownerOfValue(script,
                thenSet.inputs.get("value"));
        assertEquals(thenSet.id, owner.ownerId());
        assertEquals("value", owner.slot());
        assertEquals(branch.id, ScriptTree.containingStatement(script,
                branch.inputs.get("condition")));

        assertTrue(ScriptTree.detach(script, branch.id));
        script.nodes.removeIf(node -> subtree.contains(node.id));
        ScriptTree.prune(script);
        assertEquals(after.id, ScriptTree.first(script, ScriptTree.Slot.top()));
        assertNull(script.node(orphan.id));
        assertNull(script.node(branch.id));
        assertNull(script.node(thenSet.id));
        assertEquals(1, ScriptTree.countStatements(script));
    }

    @Test
    void movingIfPreservesBranches() {
        final FareScript script = Scripts.empty();
        final ScriptNode branch = ifNode(script, "<=");
        ScriptTree.append(script, ScriptTree.Slot.top(), branch.id);
        final ScriptNode thenSet = setFare(script, 2);
        ScriptTree.append(script, new ScriptTree.Slot(branch.id, ScriptTree.Branch.THEN), thenSet.id);
        final ScriptNode after = addFare(script, 1);
        ScriptTree.append(script, ScriptTree.Slot.top(), after.id);

        assertTrue(ScriptTree.detach(script, branch.id));
        assertNull(ScriptTree.next(script, branch.id));
        assertEquals(thenSet.id, ScriptTree.first(script,
                new ScriptTree.Slot(branch.id, ScriptTree.Branch.THEN)));
        ScriptTree.insertBefore(script, ScriptTree.Slot.top(), branch.id, after.id);
        assertEquals(branch.id, ScriptTree.first(script, ScriptTree.Slot.top()));
        assertEquals(after.id, ScriptTree.next(script, branch.id));
        assertEquals(3.0, eval(script, 1), 1e-9);
        assertEquals(1.0, eval(script, 5), 1e-9);
    }

    @Test
    void cannotNestBlockIntoItsOwnBranch() {
        final FareScript script = Scripts.empty();
        final ScriptNode branch = ifNode(script, "<=");
        ScriptTree.append(script, ScriptTree.Slot.top(), branch.id);
        final ScriptNode child = setFare(script, 2);
        ScriptTree.append(script, new ScriptTree.Slot(branch.id, ScriptTree.Branch.THEN), child.id);

        ScriptTree.append(script, new ScriptTree.Slot(branch.id, ScriptTree.Branch.THEN), branch.id);
        assertEquals(child.id, ScriptTree.first(script,
                new ScriptTree.Slot(branch.id, ScriptTree.Branch.THEN)));
        assertNull(ScriptTree.next(script, child.id));
    }

    @Test
    void valueReferenceMoveKeepsSingleOwner() {
        final FareScript script = Scripts.empty();
        final ScriptNode set = setFare(script, 2);
        ScriptTree.append(script, ScriptTree.Slot.top(), set.id);
        final String valueId = set.inputs.get("value");
        final ScriptTree.ValueOwner owner = ScriptTree.ownerOfValue(script, valueId);
        assertEquals(set.id, owner.ownerId());

        final ScriptNode other = addFare(script, 0);
        ScriptTree.append(script, ScriptTree.Slot.top(), other.id);
        ScriptTree.removeValueReferences(script, valueId);
        other.inputs.put("value", valueId);
        final ScriptTree.ValueOwner moved = ScriptTree.ownerOfValue(script, valueId);
        assertEquals(other.id, moved.ownerId());
        assertEquals(other.id, ScriptTree.containingStatement(script, valueId));
    }
}

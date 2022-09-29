import java.util.Scanner;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMLMapImpl;

import SimplStateMachine.*;
import SimplStateMachine.impl.BooleanDataImpl;
import SimplStateMachine.impl.DataImpl;
import SimplStateMachine.impl.IntegerDataImpl;
import SimplStateMachine.impl.SimplStateMachineFactoryImpl;
import SimplStateMachine.impl.VariableReferenceImpl;

public class StateMachineManipulation {

	public void sauverModele(String uri, EObject root) {
		Resource resource = null;
		try {
			URI uriUri = URI.createURI(uri);
			Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
			resource = (new ResourceSetImpl()).createResource(uriUri);
			resource.getContents().add(root);
			resource.save(null);
		} catch (Exception e) {
			System.err.println("ERREUR sauvegarde du mod�le : " + e);
			e.printStackTrace();
		}
	}

	public Resource chargerModele(String uri, EPackage pack) {
		Resource resource = null;
		try {
			URI uriUri = URI.createURI(uri);
			Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
			resource = (new ResourceSetImpl()).createResource(uriUri);
			XMLResource.XMLMap xmlMap = new XMLMapImpl();
			xmlMap.setNoNamespacePackage(pack);
			java.util.Map options = new java.util.HashMap();
			options.put(XMLResource.OPTION_XML_MAP, xmlMap);
			resource.load(options);
		} catch (Exception e) {
			System.err.println("ERREUR chargement du mod�le : " + e);
			e.printStackTrace();
		}
		return resource;
	}

	public StateMachine getModelBase(String modelFile) {
		Resource resource = chargerModele(modelFile, SimplStateMachinePackage.eINSTANCE);
		if (resource == null) {
			System.err.println(" Erreur de chargement du mod�le");
			return null;
		}

		TreeIterator<EObject> it = resource.getAllContents();

		StateMachine sm = null;
		while (it.hasNext()) {
			EObject obj = (EObject) it.next();
			if (obj instanceof StateMachine) {
				sm = (StateMachine) obj;
				break;
			}
		}
		return sm;
	}

	public void unactivateUpStateHierarchy(State s) {
		State up = s.getContainer();
		while (up != null) {
			up.setIsActive(false);
			up = up.getContainer();
		}
	}

	// Désactive la hiérarchie basse d'un état
	public void unactivateDownStateHierarchy(State s) {
		if (s instanceof CompositeState)
			for (State down : ((CompositeState) s).getStates()) {
				down.setIsActive(false);
				if (down instanceof CompositeState)
					this.unactivateDownStateHierarchy(down);
			}
	}

	// Désactive un état et toute sa hiérarchie
	public void unactivateStateHierarchy(State s) {
		s.setIsActive(false);
		this.unactivateUpStateHierarchy(s);
		this.unactivateDownStateHierarchy(s);
	}

	public void activateUpStateHierarchy(State s) {
		State up = s.getContainer();
		while (up != null) {
			up.setIsActive(true);
			up = up.getContainer();
		}
	}

	// Active la hiérarchie basse d'un état
	public void activateDownStateHierarchy(State s) {
		if (s instanceof CompositeState) {
			State init = ((CompositeState) s).getInitialState().getReferencedState();
			init.setIsActive(true);
			if (init instanceof CompositeState)
				this.activateDownStateHierarchy(init);
		}
	}

	// Active toute la hiérarchie d'un état
	public void activateStateHierarchy(State s) {
		s.setIsActive(true);
		this.activateUpStateHierarchy(s);
		this.activateDownStateHierarchy(s);
	}

	// Retourne la transition partant de l'état actif le plus bas pour l'événement
	// précisé ou null si aucune transition n'a été trouvée
	public Transition getTriggerableTransition(String evt, StateMachine sm) {
		boolean fini = false;
		State activeState = this.getLeafActiveState(sm);
		Transition trans = null;
		while (!fini) {
			for (Transition t : sm.getTransitions())
				if (t.getEvent().getName().equals(evt) && (activeState == t.getSource()) && guardIsTrue(t)) {
					trans = t;
					fini = true;
				}
			if (!fini) {
				activeState = activeState.getContainer();
				if (activeState == null)
					fini = true;
			}
		}
		return trans;

	}

	public State getLeafActiveState(CompositeState comp) {
		for (State s : comp.getStates())
			if (s.isIsActive())
				if (s instanceof CompositeState)
					return this.getLeafActiveState((CompositeState) s);
				else
					return s;
		return null;
	}

	// Initialise la machine à états en activant tous les états initiaux
	public void initStateMachine(StateMachine sm) {
		sm.setIsActive(true);
		State s = sm.getInitialState().getReferencedState();
		while (s != null) {
			s.setIsActive(true);
			if (s instanceof CompositeState) {
				s = ((CompositeState) s).getInitialState().getReferencedState();
				processOperation(s.getOperation());
			}
				
			else {
				processOperation(s.getOperation());
				s = null;
			}
				
		}
	}

	public void processOperation(Operation o) {
		for (Assignment a : o.getContents()) {
			//System.out.println("Name of assignment " + a.get_name());
			//System.out.println(a.getVariable().getName() + " Value: " + a.getVariable().getValue());
			//System.out.println("Expression : " + a.getExpression());
			SimplStateMachineFactory factory = new SimplStateMachineFactoryImpl();
			Data data = null;
			if (evaluate(a.getExpression()) instanceof IntegerData) {
				data = factory.createIntegerData();
				((IntegerData) data).setValue(((IntegerData) evaluate(a.getExpression())).getValue());
			} else if (evaluate(a.getExpression()) instanceof BooleanData) {
				data = factory.createBooleanData();
				((BooleanData) data).setValue(((BooleanData) evaluate(a.getExpression())).isValue());
			}
			
			a.getVariable().setValue(data);
			
			System.out.println(a.getVariable().getName() + " new value is: " + a.getVariable().getValue());
		}
	}

	public void processEvent(String event, StateMachine sm) {
		Transition trans = this.getTriggerableTransition(event, sm);
		if (trans != null) {	
			
			System.out.println("New transition is: " + trans.getEvent() + " " + trans.getGuard());

			this.unactivateStateHierarchy(trans.getSource());
			State target = trans.getTarget();
			this.activateStateHierarchy(target);
			
			System.out.println("New state is: " + target.getName());
			
			if (target instanceof CompositeState) {
				target = getLeafActiveState((CompositeState) target);
				System.out.println("New state is: " + target.getName());
			}
			
			if (target.getOperation() != null) {
				processOperation(target.getOperation());
			}
		}
	}

	private boolean guardIsTrue(Transition trans) {
		if (trans.getGuard() == null) return true;
		return ((BooleanData) evaluate(trans.getGuard())).isValue();
	}

	private IntegerData evaluateIntegerOperation(Operator op, IntegerData leftData, IntegerData rightData) {
		SimplStateMachineFactory factory = new SimplStateMachineFactoryImpl();
		IntegerData result = factory.createIntegerData();

		int leftValue = leftData.getValue();
		int rightValue = rightData.getValue();

		switch (op) {
		case ADD:
			//System.out.println(leftValue + " + " + rightValue + " == " + (leftValue + rightValue));
			result.setValue(leftValue + rightValue);
			return result;
		case SUB:
			//System.out.println(leftValue + " - " + rightValue);
			result.setValue(leftValue - rightValue);
			return result;
		case MUL:
			//System.out.println(leftValue + " * " + rightValue);
			result.setValue(leftValue * rightValue);
			return result;
		case DIV:
			//System.out.println(leftValue + " / " + rightValue);
			result.setValue(leftValue / rightValue);
			return result;
		default:
			break;
		}

		return result;

	}

	private BooleanData evaluateBooleanOperation(Operator op, Data leftData, Data rightData) {
		SimplStateMachineFactory factory = new SimplStateMachineFactoryImpl();
		BooleanData result = factory.createBooleanData();

		// TRAITEMENT DU CAS PARTICULIER DU NOT
		if (op.equals(Operator.NOT)) {
			result.setValue(!((BooleanData) leftData).isValue());
			//System.out.println("! " + result.isValue());
			return result;
		}

		// TRAITEMENT DES OPERATIONS EQ ET NEQ EN FONCTION DU TYPE DES ELEMENTS
		if (leftData instanceof BooleanData && rightData instanceof BooleanData) {
			switch (op) {
			case EQ:
				//System.out.println(((BooleanData) leftData).isValue() + "==" + ((BooleanData) rightData).isValue());
				result.setValue(((BooleanData) leftData).isValue() == ((BooleanData) rightData).isValue());
				return result;
			case NEQ:
				//System.out.println(((BooleanData) leftData).isValue() + "!=" + ((BooleanData) rightData).isValue());
				result.setValue(!(((BooleanData) leftData).isValue() == ((BooleanData) rightData).isValue()));
				return result;
			default:
				break;
			}
		} else if (leftData instanceof IntegerData && rightData instanceof IntegerData) {
			switch (op) {
			case EQ:
				//System.out.println(((IntegerData) leftData).getValue() + "==" + ((IntegerData) rightData).getValue());
				result.setValue(((IntegerData) leftData).getValue() == ((IntegerData) rightData).getValue());
				return result;
			case NEQ:
				//System.out.println(((IntegerData) leftData).getValue() + "!=" + ((IntegerData) rightData).getValue());
				result.setValue(!(((IntegerData) leftData).getValue() == ((IntegerData) rightData).getValue()));
				return result;
			default:
				break;
			}
		}

		// TRAITEMENT GENERAL
		switch (op) {
		case GT:
			//System.out.println(((IntegerData) leftData).getValue() + " > " + ((IntegerData) rightData).getValue());
			result.setValue(((IntegerData) leftData).getValue() > ((IntegerData) rightData).getValue());
			return result;
		case GTE:
			//System.out.println(((IntegerData) leftData).getValue() + " >= " + ((IntegerData) rightData).getValue());
			result.setValue(((IntegerData) leftData).getValue() >= ((IntegerData) rightData).getValue());
			return result;
		case LT:
			//System.out.println(((IntegerData) leftData).getValue() + " < " + ((IntegerData) rightData).getValue());
			result.setValue(((IntegerData) leftData).getValue() < ((IntegerData) rightData).getValue());
			return result;
		case LTE:
			//System.out.println(((IntegerData) leftData).getValue() + " >= " + ((IntegerData) rightData).getValue());
			result.setValue(((IntegerData) leftData).getValue() <= ((IntegerData) rightData).getValue());
			return result;
		case AND:
			//System.out.println(((BooleanData) leftData).isValue() + " && " + ((BooleanData) rightData).isValue());
			result.setValue(((BooleanData) leftData).isValue() && ((BooleanData) rightData).isValue());
			return result;
		case OR:
			//System.out.println(((BooleanData) leftData).isValue() + " || " + ((BooleanData) rightData).isValue());
			result.setValue(((BooleanData) leftData).isValue() || ((BooleanData) rightData).isValue());
			return result;
		default:
			break;
		}

		return result;
	}

	private Data evaluate(ExpressionElement ex) {

		if (ex instanceof VariableReference) {
			return ((VariableReference) ex).getVariable().getValue();
		}
		if (ex instanceof Data) {
			return (Data) ex;
		}

		Operator op = ((Expression) ex).getOperator();
		ExpressionElement leftElement = ((Expression) ex).getLeft();
		ExpressionElement rightElement = ((Expression) ex).getRight();

		Data leftData = null;
		Data rightData = null;

		// CHECK SI LE RESULTAT DE L'OPERATION EST CENSE ETRE UN ENTIER
		if (isIntegerResult(op)) {
			if (leftElement instanceof VariableReference) {
				leftData = (IntegerData) ((VariableReference) leftElement).getVariable().getValue();
			} else if (leftElement instanceof IntegerData) {
				leftData = (IntegerData) leftElement;
			} else {
				leftData = (IntegerData) evaluate((Expression) leftElement);
			}

			if (rightElement instanceof VariableReference) {
				rightData = (IntegerData) ((VariableReference) rightElement).getVariable().getValue();
			} else if (rightElement instanceof IntegerData) {
				rightData = (IntegerData) rightElement;
			} else {
				rightData = evaluate((Expression) rightElement);
			}

			return evaluateIntegerOperation(op, (IntegerData) leftData, (IntegerData) rightData);

		}
		// CHECK SI LE RESULTAT DE L'OPERATION EST CENSE ETRE UN BOOLEEN
		else if (isBooleanResult(op)) {
			if (leftElement instanceof VariableReference) {
				leftData = ((VariableReference) leftElement).getVariable().getValue();
			} else if (leftElement instanceof Data) {
				leftData = (Data) leftElement;
			} else {
				leftData = (Data) evaluate((Expression) leftElement);
			}

			if (rightElement instanceof VariableReference) {
				rightData = ((VariableReference) rightElement).getVariable().getValue();
			} else if (rightElement instanceof Data) {
				rightData = (Data) rightElement;
			} else {
				rightData = (Data) evaluate((Expression) rightElement);
			}
			return evaluateBooleanOperation(op, leftData, rightData);
		}
		return null;

	}

	private boolean isIntegerResult(Operator op) {
		//System.out.println("isIntegerResult?");
		return op.equals(Operator.ADD) || op.equals(Operator.SUB) || op.equals(Operator.MUL) || op.equals(Operator.DIV);
	}

	private boolean isBooleanResult(Operator op) {
		//System.out.println("isBooleanResult?");
		return op.equals(Operator.AND) || op.equals(Operator.OR) || op.equals(Operator.NOT) || op.equals(Operator.GT)
				|| op.equals(Operator.GTE) || op.equals(Operator.LT) || op.equals(Operator.LTE)
				|| op.equals(Operator.EQ) || op.equals(Operator.NEQ);
	}

	public void executeStateMachine(StateMachine sm) {

		this.initStateMachine(sm);

		Scanner scan = new Scanner(System.in);
		String event = "";

		while (!event.equals("end")) {
			System.out.print("Entrez le nom d'un �v�nement (\"end\" pour terminer) : ");
			event = scan.nextLine();
			if (!event.equals("end"))
				processEvent(event, sm);
		}

	}

	public static void main(String argv[]) {

		StateMachineManipulation util = new StateMachineManipulation();

		System.out.println(" Chargement du modele");
		StateMachine sm = util.getModelBase("models/Voiture.xmi");
		System.out.println(" Modele charge");

		util.executeStateMachine(sm);
	}
}
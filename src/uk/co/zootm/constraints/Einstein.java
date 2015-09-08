package uk.co.zootm.constraints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jacop.constraints.Alldifferent;
import org.jacop.constraints.IfThen;
import org.jacop.constraints.Or;
import org.jacop.constraints.PrimitiveConstraint;
import org.jacop.constraints.XeqC;
import org.jacop.core.IntVar;
import org.jacop.core.Store;
import org.jacop.search.DepthFirstSearch;
import org.jacop.search.IndomainMin;
import org.jacop.search.InputOrderSelect;
import org.jacop.search.Search;
import org.jacop.search.SelectChoicePoint;

public class Einstein {

	private static final Store store = new Store();
	private final List<Person> people;

	public static void main(String[] args) {
		new Einstein().run();

	}

	public Einstein() {
		people = new ArrayList<Person>();
		for (int i = 0; i < 5; i++) {
			people.add(new Person());
		}
	}

	public void run() {
		store.impose(new Alldifferent(variables(x -> x.houseColor)));
		store.impose(new Alldifferent(variables(x -> x.nationality)));
		store.impose(new Alldifferent(variables(x -> x.beverage)));
		store.impose(new Alldifferent(variables(x -> x.cigar)));
		store.impose(new Alldifferent(variables(x -> x.pet)));

		for (int i = 0; i < people.size(); i++) {

			Person person = people.get(i);
			Person left = i > 0 ? people.get(i - 1) : null;
			Person right = i < people.size() - 1 ? people.get(i + 1) : null;
			// 1. The Brit lives in a red house.
			store.impose(new IfThen(eq(person.nationality, Nationality.Brit), eq(person.houseColor, Color.Red)));

			// 2. The Swede keeps dogs as pets.
			store.impose(new IfThen(eq(person.nationality, Nationality.Swede), eq(person.pet, Pet.Dog)));

			// 3. The Dane drinks tea.
			store.impose(new IfThen(eq(person.nationality, Nationality.Dane), eq(person.beverage, Beverage.Tea)));

			// 4. The green house is on the left of the white house (next to it).
			if (left != null) {
				store.impose(new IfThen(eq(person.houseColor, Color.White), eq(left.houseColor, Color.Green)));
			}

			// 5. The green house owner drinks coffee.
			store.impose(new IfThen(eq(person.houseColor, Color.Green), eq(person.beverage, Beverage.Coffee)));

			// 6. The person who smokes Pall Mall rears birds.
			store.impose(new IfThen(eq(person.cigar, Cigar.PallMall), eq(person.pet, Pet.Bird)));

			// 7. The owner of the yellow house smokes Dunhill.
			store.impose(new IfThen(eq(person.houseColor, Color.Yellow), eq(person.cigar, Cigar.Dunhill)));

			// 8. The man living in the house right in the center drinks milk.
			if (i == 2) {
				store.impose(eq(person.beverage, Beverage.Milk));
			}

			// 9. The Norwegian lives in the first house.
			if (i == 0) {
				store.impose(eq(person.nationality, Nationality.Norwegian));
			}

			// 10. The man who smokes blend lives next to the one who keeps cats.
			store.impose(new IfThen(eq(person.cigar, Cigar.Blend), //
					neighbourConstraint(left, right, x -> eq(x.pet, Pet.Cat))));

			// 11. The man who keeps horses lives next to the man who smokes Dunhill.
			store.impose(new IfThen(eq(person.pet, Pet.Horse), //
					neighbourConstraint(left, right, x -> eq(x.cigar, Cigar.Dunhill))));

			// 12. The owner who smokes Blue Master drinks beer.
			store.impose(new IfThen(//
					eq(person.cigar, Cigar.BlueMaster), eq(person.beverage, Beverage.Beer)));

			// 13. The German smokes Prince.
			store.impose(new IfThen(//
					eq(person.nationality, Nationality.German), eq(person.cigar, Cigar.Prince)));

			// 14. The Norwegian lives next to the blue house.
			store.impose(new IfThen(eq(person.nationality, Nationality.Norwegian), //
					neighbourConstraint(left, right, x -> eq(x.houseColor, Color.Blue))));

			// 15. The man who smokes blend has a neighbor who drinks water.
			store.impose(new IfThen(eq(person.cigar, Cigar.Blend), //
					neighbourConstraint(left, right, x -> eq(x.beverage, Beverage.Water))));
		}

		if (!store.consistency()) {
			throw new IllegalStateException("Inconsistent!");
		}

		System.out.println("Consistent? " + store.consistency());

		IntVar[] allVars = people.stream().flatMap( //
				x -> Stream.of(x.houseColor, x.nationality, x.beverage, x.cigar, x.pet)).toArray(i -> new IntVar[i]);

		Search<IntVar> label = new DepthFirstSearch<IntVar>();
		SelectChoicePoint<IntVar> select = new InputOrderSelect<IntVar>(store, allVars, new IndomainMin<IntVar>());

		boolean result = label.labeling(store, select);

		if (!result) {
			throw new IllegalStateException("Unsolvable!");

		}

		for (Person person : people) {
			System.out.println(person);
		}
	}

	private PrimitiveConstraint neighbourConstraint(Person left, Person right, Function<Person, PrimitiveConstraint> constraint) {
		PrimitiveConstraint neighbourConstraint;
		if (left != null && right != null) {
			neighbourConstraint = new Or(constraint.apply(left), constraint.apply(right));
		} else if (left != null) {
			neighbourConstraint = constraint.apply(left);
		} else {
			neighbourConstraint = constraint.apply(right);
		}
		return neighbourConstraint;
	}

	private XeqC eq(IntVar var, Enum<?> val) {
		return new XeqC(var, val.ordinal());
	}

	private IntVar[] variables(Function<Person, IntVar> extractor) {
		return people.stream().map(extractor).toArray(x -> new IntVar[x]);
	}

	private class Person {
		private final IntVar houseColor;
		private final IntVar nationality;
		private final IntVar beverage;
		private final IntVar cigar;
		private final IntVar pet;

		public Person() {
			this.houseColor = new IntVar(store, 0, Color.values().length - 1);
			this.nationality = new IntVar(store, 0, Nationality.values().length - 1);
			this.beverage = new IntVar(store, 0, Beverage.values().length - 1);
			this.cigar = new IntVar(store, 0, Cigar.values().length - 1);
			this.pet = new IntVar(store, 0, Pet.values().length - 1);
		}

		@Override
		public String toString() {
			// Crappiest toString
			Object[] values = { Color.values()[houseColor.value()], //
					Nationality.values()[nationality.value()],//
					Beverage.values()[beverage.value()], //
					Cigar.values()[cigar.value()], //
					Pet.values()[pet.value()] //
			};

			return Arrays.toString(values);
		}
	}

	private enum Color {
		Blue, Green, Red, White, Yellow
	}

	private enum Nationality {
		Brit, Dane, German, Norwegian, Swede
	}

	private enum Beverage {
		Beer, Coffee, Milk, Tea, Water
	}

	private enum Cigar {
		BlueMaster, Dunhill, PallMall, Prince, Blend
	}

	private enum Pet {
		Cat, Bird, Dog, Fish, Horse
	}
}

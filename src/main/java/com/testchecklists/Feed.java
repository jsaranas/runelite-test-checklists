package com.testchecklists;

import java.util.ArrayList;
import java.util.List;

/** The checklists.json feed. Field names are the JSON keys; see the README. */
class Feed
{
	String generated;
	List<Checklist> checklists = new ArrayList<>();

	static class Checklist
	{
		String id;
		String title;
		String area;
		String source;
		String setup;
		String notes;
		List<Item> items = new ArrayList<>();

		@Override
		public String toString()
		{
			return (area == null || area.isEmpty() ? "" : area + ": ") + title;
		}
	}

	static class Item
	{
		int n;
		String text;
		String expect;
		/** Already passed (by another tester, or in an earlier round). */
		boolean passed;
	}
}

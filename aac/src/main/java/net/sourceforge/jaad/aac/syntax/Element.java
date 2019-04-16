package net.sourceforge.jaad.aac.syntax;

public abstract class Element implements Constants {

	protected int elementInstanceTag;

	protected int readElementInstanceTag(BitStream in) {
		elementInstanceTag = in.readBits(4);
		return elementInstanceTag;
	}

	public int getElementInstanceTag() {
		return elementInstanceTag;
	}
}

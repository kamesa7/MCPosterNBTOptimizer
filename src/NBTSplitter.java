import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import javax.swing.JOptionPane;

import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.IntTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.Tag;

public class NBTSplitter {
	public static void main(String[] args) {
		try {
			if (args.length < 1) {
				FileDialog dialog = new FileDialog(new Frame());
				dialog.setMode(FileDialog.LOAD);
				dialog.setMultipleMode(false);
				dialog.setTitle("Select NBT file");
				dialog.setVisible(true);
				new NBTSplitter(dialog.getFiles()[0]);
			} else {
				new NBTSplitter(new File(args[0]));
			}
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, e);
		}
		System.exit(0);
	}

	public NBTSplitter(File file) throws IOException {
		ListTag<CompoundTag> blocks;
		ListTag<IntTag> size;
		NamedTag rawtag;
		rawtag = NBTUtil.read(file);
		Tag<?> fulltag = rawtag.getTag();
		CompoundTag cpt = (CompoundTag) fulltag;
		blocks = cpt.getListTag("blocks").asCompoundTagList();
		size = cpt.getListTag("size").asIntTagList();
		int X = size.get(0).asInt();
		int Y = size.get(1).asInt();
		int Z = size.get(2).asInt();
		System.out.println(
				String.format("Loaded: %s  size:(%d, %d, %d)  blocks: %d ", file.getName(), X, Y, Z, blocks.size()));

		int split = Integer.parseInt(JOptionPane.showInputDialog("Set Split Count"));

		int d = X / split;
		for (int i = 0; i < split; i++) {
			String name = String.format("%s-p%d.nbt", file.getName().substring(0, file.getName().length() - 4), i + 1);
			if (i + 1 < split) {
				split(file, i * d, (i + 1) * d, name);
			} else {
				split(file, i * d, X, name);
			}
		}

		System.out.println(split + "-Split Completed!");
	}

	void split(File file, int from, int to, String name) throws IOException {
		ListTag<CompoundTag> blocks;
		ListTag<IntTag> size;
		NamedTag rawtag;
		rawtag = NBTUtil.read(file);
		Tag<?> fulltag = rawtag.getTag();
		CompoundTag cpt = (CompoundTag) fulltag;
		blocks = cpt.getListTag("blocks").asCompoundTagList();
		size = cpt.getListTag("size").asIntTagList();
		size.set(0, new IntTag(to - from));
		ArrayList<Integer> indexfilter = new ArrayList<Integer>();
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag block = blocks.get(i);
			ListTag<IntTag> pos = block.getListTag("pos").asIntTagList();
			int x = pos.get(0).asInt();
			if (x < from || to <= x) {
				indexfilter.add(i);
			} else {
				pos.set(0, new IntTag(x - from));
			}
		}
		Collections.sort(indexfilter, Comparator.reverseOrder());
		for (int index : indexfilter) {
			blocks.remove(index);
		}
		NBTUtil.write(rawtag, name);
		System.out.println("Saved " + name);
	}
}
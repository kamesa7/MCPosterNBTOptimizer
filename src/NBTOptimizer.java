import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.IntTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.Tag;

public class NBTOptimizer {
	public static void main(String[] args) {
		try {
			if (args.length < 1) {
				System.out.println("nbt file is required");
				return;
			}
			new NBTOptimizer(new File(args[0]));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static int YLIMIT = 100;
	static int MOVELIMIT = 5;
	static boolean LOG = false;
	static String UNDERBLOCK = "";

	final int X;
	final int Y;
	final int Z;
	final int[][] originalmap;
	final Pixel[][] pixelmap;

	public NBTOptimizer(File file) throws IOException {
		Properties properties = new Properties();
		properties.load(new FileReader(new File("settings.properties")));
		YLIMIT = Integer.parseInt(properties.getProperty("Ylimit"));
		MOVELIMIT = Integer.parseInt(properties.getProperty("Warplimit"));
		LOG = Boolean.parseBoolean(properties.getProperty("Log"));
		UNDERBLOCK = properties.getProperty("UnderBlock");

		NamedTag rawtag = NBTUtil.read(file);
		System.out.println(rawtag.getName());
		Tag<?> fulltag = rawtag.getTag();
		CompoundTag cpt = (CompoundTag) fulltag;
		System.out.println(cpt.keySet());
		/*
		 * for (Entry<String, Tag<?>> entry : cpt.entrySet()) { String value =
		 * entry.getValue().toString(); System.out.println(entry.getKey() + " : " +
		 * value.substring(0, Math.min(value.length(), 175))); }
		 */
		int underblockid = -1;
		ListTag<CompoundTag> palette = cpt.getListTag("palette").asCompoundTagList();
		for (int i = 0; i < palette.size(); i++) {
			CompoundTag tag = palette.get(i);
			String blockname = tag.getString("Name");
			if (blockname.equals(UNDERBLOCK)) {
				underblockid = i;
				System.out.println("UnderBlockID: " + i);
			}
		}
//		System.out.println(palette.size());

		ListTag<IntTag> size = cpt.getListTag("size").asIntTagList();
//		System.out.println(size.size());
		X = size.get(0).asInt();
		Y = size.get(1).asInt();
		Z = size.get(2).asInt();
		System.out.println(String.format("original size (%d, %d, %d)", X, Y, Z));
		originalmap = new int[X][Z];

		ListTag<CompoundTag> blocks = cpt.getListTag("blocks").asCompoundTagList();
		System.out.println(blocks.size() + " blocks used");

		for (CompoundTag tag : blocks) {
			ListTag<IntTag> pos = tag.getListTag("pos").asIntTagList();
			int x = pos.get(0).asInt();
			int y = pos.get(1).asInt();
			int z = pos.get(2).asInt();
			originalmap[x][z] = Math.max(originalmap[x][z], y);
		}

		pixelmap = new Pixel[X][Z];
		for (int x = 0; x < X; x++) {
			for (int z = 0; z < Z; z++) {
				pixelmap[x][z] = new Pixel(x, originalmap[x][z], z);
			}
		}
		for (int x = 0; x < X; x++) {
			for (int z = 0; z < Z; z++) {
				pixelmap[x][z].init(pixelmap);
			}
		}

		managers = new LineManager[X];
		for (int x = 0; x < X; x++) {
			managers[x] = new LineManager(x);
		}
		sorted = new ArrayList<LineManager>(X);
		for (LineManager lm : managers)
			sorted.add(lm);
		sorted.sort(null);
		int changecnt = 0;
		while (sorted.get(0).update > 0) {
			sorted.get(0).dequeue();
			sorted.sort(null);
			changecnt++;
		}

		int outputy = 0;
		for (int x = 0; x < X; x++) {
			for (int z = 0; z < Z; z++) {
				outputy = Math.max(pixelmap[x][z].y + 1, outputy);
			}
		}

		System.out.println(String.format("optimized size (%d, %d, %d)", X, outputy, Z));
		System.out.println(changecnt + " moves operated");

		size.set(1, new IntTag(outputy));

		boolean[][][] schematic = new boolean[X][outputy][Z];
		for (CompoundTag tag : blocks) {
			IntTag idtag = tag.getIntTag("state");
			ListTag<IntTag> pos = tag.getListTag("pos").asIntTagList();
			int x = pos.get(0).asInt();
			int z = pos.get(2).asInt();
			int ny = pixelmap[x][z].y;
			if (idtag.asInt() != underblockid) {
				pos.set(1, new IntTag(ny));
				schematic[x][ny][z] = true;
			} else {
				int undery = Math.max(0, ny - 1);
				if (schematic[x][undery][z]) {
					undery = Math.max(0, undery - 1);
				}
				pos.set(1, new IntTag(undery));
				schematic[x][undery][z] = true;
			}
		}
		NBTUtil.write(rawtag,
				String.format("%s-optimized.nbt", file.getName().substring(0, file.getName().length() - 4)));
		System.out.println("optimize complete");
	}

	LineManager[] managers;
	List<LineManager> sorted;

	class LineManager implements Comparable<LineManager> {
		int x;
		Operation best;
		int update;

		public LineManager(int x) {
			this.x = x;
			refresh();
		}

		void refresh() {
			update = 0;
			for (int a = MOVELIMIT; a >= 1; a--) {
				for (int z = 0; z < Z; z++) {
					int num = pixelmap[x][z].checkup(a);
					if (update < num) {
						update = num;
						best = new Operation(pixelmap[x][z], a);
					}
				}
			}
		}

		void dequeue() {
			best.operate();
			refresh();
			if (x > 1)
				managers[x - 1].refresh();
			if (x < X - 2)
				managers[x + 1].refresh();
		}

		@Override
		public int compareTo(NBTOptimizer.LineManager o) {
			return o.update - this.update;
		}

		@Override
		public String toString() {
			return String.format("%d-%d", x, update);
		}
	}
}

class Operation {
	final Pixel target;
	final int amount;

	public Operation(Pixel target, int amount) {
		this.target = target;
		this.amount = amount;
	}

	void operate() {
		target.operateup(amount);
	}
}

class Pixel {
	final int x;
	int y;
	final int z;
	Pixel upp;
	Pixel upm;
	Pixel downp;
	Pixel downm;
	Pixel samep;
	Pixel samem;
	Pixel sidep;
	Pixel sidem;

	Pixel(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	private int nexty(int ny) {
		return Math.max(ny + 1, y);
	}

	private int diff() {
		int p = sidep != null ? transfunc(y, sidep.y) : 0;
		int m = sidem != null ? transfunc(y, sidem.y) : 0;
		return p + m;
	}

	private int ifup(int ny) {
		if (ny >= NBTOptimizer.YLIMIT)
			return 1 << 20;
		int p = sidep != null ? transfunc(ny, sidep.y) : 0;
		int m = sidem != null ? transfunc(ny, sidem.y) : 0;
		return p + m;
	}

	private int transfunc(int thisy, int sidey) {
		int d = sidey - thisy;
		int ad = Math.abs(d);
		if (ad >= 2)
			return 2;
		return ad;
	}

	int checkup(int a) {
		if (samep != null && samem != null)
			return 0;

		int now = recdiff(null);
		int ifup = recifup(y + a, null);

		if (ifup < now) {
			return now - ifup;
		}
		return 0;
	}

	void operateup(int a) {
		int now = recdiff(null);
		int ifup = recifup(y + a, null);
		if (NBTOptimizer.LOG)
			System.out.println(String.format("(%d,%d) y: %d -> %d  (%d -> %d)", x, z, y, y + a, now, ifup));
		recsetup(y + a, null);
	}

	private int recdiff(Pixel from) {
		int diff = diff();
		if (upp != null && upp != from)
			diff += upp.recdiff(this);
		if (upm != null && upm != from)
			diff += upm.recdiff(this);
		if (samep != null && samep != from)
			diff += samep.recdiff(this);
		if (samem != null && samem != from)
			diff += samem.recdiff(this);
		return diff;
	}

	private int recifup(int ny, Pixel from) {
		int diff = ifup(ny);
		if (upp != null && upp != from)
			diff += upp.recifup(upp.nexty(ny), this);
		if (upm != null && upm != from)
			diff += upm.recifup(upm.nexty(ny), this);
		if (samep != null && samep != from)
			diff += samep.recifup(ny, this);
		if (samem != null && samem != from)
			diff += samem.recifup(ny, this);
		return diff;
	}

	private void recsetup(int ny, Pixel from) {
		this.y = ny;
		if (upp != null && upp != from)
			upp.recsetup(upp.nexty(y), this);
		if (upm != null && upm != from)
			upm.recsetup(upm.nexty(y), this);
		if (samep != null && samep != from)
			samep.recsetup(y, this);
		if (samem != null && samem != from)
			samem.recsetup(y, this);
	}

	void init(Pixel[][] map) {
		try {
			this.sidem = map[x - 1][z];
		} catch (IndexOutOfBoundsException e) {
			this.sidem = null;
		}
		try {
			this.sidep = map[x + 1][z];
		} catch (IndexOutOfBoundsException e) {
			this.sidem = null;
		}

		try {
			Pixel up = map[x][z + 1];

			if (up.y > y) {
				upp = up;
			} else if (up.y < y) {
				downp = up;
			} else {
				samep = up;
			}
		} catch (IndexOutOfBoundsException e) {
		}

		try {
			Pixel down = map[x][z - 1];
			if (down.y > y) {
				upm = down;
			} else if (down.y < y) {
				downm = down;
			} else {
				samem = down;
			}
		} catch (IndexOutOfBoundsException e) {
		}
	}

	@Override
	public String toString() {
		return String.format("(%d, %d, %d)  upp:%d  upm:%d  sidep:%d  sidem:%d", x, y, z, upp != null ? upp.y : null,
				upm != null ? upm.y : null, sidep != null ? sidep.y : null, sidem != null ? sidem.y : null);
	}
}
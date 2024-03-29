import java.util.Set;

public class Pixel {
	final int x;
	final int defy;
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
	private int connect = 0;
	final int id;
	int under = 0;

	void recconnect(int connectkey) {
		if (connected(connectkey))
			return;
		connect = connectkey;
		if (upp != null && upp.y == y + 1)
			upp.recconnect(connectkey);
		if (upm != null && upm.y == y + 1)
			upm.recconnect(connectkey);
		// no down connect
		if (samep != null && samep.y == y)
			samep.recconnect(connectkey);
		if (samem != null && samem.y == y)
			samem.recconnect(connectkey);
		if (sidep != null && sidep.y == y)
			sidep.recconnect(connectkey);
		if (sidem != null && sidem.y == y)
			sidem.recconnect(connectkey);
	}

	void getconnect(int connectkey) {
		if (connected(connectkey))
			return;
		if (y == 0)
			recconnect(connectkey);
		// no down connect (get from upper)
		else if (downp != null && downp.y == y - 1 && downp.connected(connectkey))
			recconnect(connectkey);
		else if (downm != null && downm.y == y - 1 && downm.connected(connectkey))
			recconnect(connectkey);
		else if (samep != null && samep.y == y && samep.connected(connectkey))
			recconnect(connectkey);
		else if (samem != null && samem.y == y && samem.connected(connectkey))
			recconnect(connectkey);
		else if (sidep != null && sidep.y == y && sidep.connected(connectkey))
			recconnect(connectkey);
		else if (sidem != null && sidem.y == y && sidem.connected(connectkey))
			recconnect(connectkey);
	}

	boolean connected(int connectkey) {
		return connect == connectkey;
	}

	public boolean catchconnect(int connectkey) {
		if (connected(connectkey))
			return true;
		int beforecatch = y;
		if (samep != null)
			y = samep.y;
		if (samem != null)
			y = samem.y;
		getconnect(connectkey);
		while (!connected(connectkey)) {
			if (downable(this)) {
				recdown(connectkey);
			} else {
				if (NBTOptimizer.LOG) {
					System.out.println(String.format("(%d,%d) catch fail y: %d", x, z, y));
				}
				y = beforecatch;
				return false;
			}
		}
		if (NBTOptimizer.LOG) {
			System.out.println(String.format("(%d,%d) catch success y: %d -> %d", x, z, beforecatch, y));
		}
		return true;
	}

	private boolean downable(Pixel from) {
		return (y >= 1) && (downp == null || downp.y < y - 1 || downp.downable(this))
				&& (downm == null || downm.y < y - 1 || downm.downable(this))
				&& (samep == from || samep == null || samep.downable(this))
				&& (samem == from || samem == null || samem.downable(this));
	}

	private void recdown(int connectkey) {
		y--;
		getconnect(connectkey);
		if (downp != null && downp.y == y)
			downp.recdown(connectkey);
		if (downm != null && downm.y == y)
			downm.recdown(connectkey);
		if (samep != null && samep.y != y)
			samep.recdown(connectkey);
		if (samem != null && samem.y != y)
			samem.recdown(connectkey);
	}

	Pixel(int x, int y, int z, int id) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.defy = y;
		this.id = id;
	}

	void cntun() {
		under++;
	}

	void optun(Set<Integer> needsset) {
		int sidefactor = 0;
		int downfactor = 0;
		int samefactor = 0;
		// 1階段
		if (downp != null && downp.y + 1 == y) {
			if (downp.needunder(needsset))
				downfactor = Math.max(downfactor, 2);
			else
				downfactor = Math.max(downfactor, 1);
		}
		if (downm != null && downm.y + 1 == y) {
			if (downm.needunder(needsset))
				downfactor = Math.max(downfactor, 2);
			else
				downfactor = Math.max(downfactor, 1);
		}
		// 2ワープ階段
		if (downp != null && downp.y + 2 == y) {
			if (downp.needunder(needsset))
				downfactor = Math.max(downfactor, 3);
			else
				downfactor = Math.max(downfactor, 2);
		}
		if (downm != null && downm.y + 2 == y) {
			if (downm.needunder(needsset))
				downfactor = Math.max(downfactor, 3);
			else
				downfactor = Math.max(downfactor, 2);
		}
		// 隣接不完全ブロック
		if (samep != null && samep.needunder(needsset)) {
			samefactor = Math.max(samefactor, 1);
		}
		if (samem != null && samem.needunder(needsset)) {
			samefactor = Math.max(samefactor, 1);
		}
		if (sidep != null && sidep.y == y && sidep.needunder(needsset)) {
			sidefactor = Math.max(sidefactor, 1);
		}
		if (sidem != null && sidem.y == y && sidem.needunder(needsset)) {
			sidefactor = Math.max(sidefactor, 1);
		}
		// 横階段
		if (!(samep != null && samem != null)) {
			if (sidep != null && sidep.y + 1 == y) {
				if (sidep.needunder(needsset))
					sidefactor = Math.max(sidefactor, 2);
				else
					sidefactor = Math.max(sidefactor, 1);
			}
			if (sidem != null && sidem.y + 1 == y) {
				if (sidem.needunder(needsset))
					sidefactor = Math.max(sidefactor, 2);
				else
					sidefactor = Math.max(sidefactor, 1);
			}
		}
		// 自身と節約
		int need = Math.max(sidefactor, Math.max(downfactor, samefactor));
		if (needunder(needsset)) {
			need = Math.max(need, 1);
		} else if (NBTOptimizer.ECOUNDERBLOCK && (sidep != null && sidep.y == y && !sidep.needunder(needsset))
				&& (sidem != null && sidem.y == y && !sidem.needunder(needsset))) {
			need = 0;
		}
		// 地面設置
		need = Math.min(y, need);

		if (NBTOptimizer.LOG && need != under) {
			System.out.println(String.format("(%d,%d) under: %d -> %d", x, z, under, need));
		}
		under = need;
	}

	void addal(int mode, int limit, Set<Integer> al) {
		if (mode >= 0 && sidep != null) {
			int d = sidep.y - y;
			if (1 <= d && d <= limit)
				al.add(d);
			if (1 <= d - 1 && d - 1 <= limit)
				al.add(d - 1);
			if (1 <= d + 1 && d + 1 <= limit)
				al.add(d + 1);
		}
		if (mode <= 0 && sidem != null) {
			int d = sidem.y - y;
			if (1 <= d && d <= limit)
				al.add(d);
			if (1 <= d - 1 && d - 1 <= limit)
				al.add(d - 1);
			if (1 <= d + 1 && d + 1 <= limit)
				al.add(d + 1);
		}
	}

	private int nexty(int ny) {
		return Math.max(ny + 1, y);
	}

	public int diff(int mode) {
		int ret = 0;
		if (mode >= 0 && sidep != null)
			ret += transfunc(y, sidep.y);
		if (mode <= 0 && sidem != null)
			ret += transfunc(y, sidem.y);
		return ret;
	}

	private int ifup(int ny, int mode) {
		if (ny >= NBTOptimizer.YLIMIT)
			return 1 << 20;
		int ret = 0;
		if (mode >= 0)
			ret += (sidep != null ? transfunc(ny, sidep.y) : 0);
		if (mode <= 0)
			ret += (sidem != null ? transfunc(ny, sidem.y) : 0);
		return ret;
	}

	private int transfunc(int thisy, int sidey) {
		int d = sidey - thisy;
		int ad = Math.abs(d);
		switch (ad) {
		case 0:
			break;
		case 1:
			ad = 2;
			break;
		default:
			ad = 3;
		}
		return ad;
	}

	int checkup(int a, int mode) {
		if (samep != null && samem != null)
			return 0;

		int now = recdiff(null, mode, NBTOptimizer.VALUERANGE);
		int ifup = recifup(y + a, null, mode, NBTOptimizer.VALUERANGE);

		if (ifup < now) {
			return now - ifup;
		}
		return 0;
	}

	void operateup(int a) {
		if (NBTOptimizer.LOG) {
			int now = recdiff(null, 0, NBTOptimizer.VALUERANGE);
			int ifup = recifup(y + a, null, 0, NBTOptimizer.VALUERANGE);
			System.out.println(String.format("(%d,%d) y: %d -> %d  (%d -> %d)", x, z, y, y + a, now, ifup));
		}
		recsetup(y + a, null);
	}

	private int recdiff(Pixel from, int mode, int range) {
		int diff = diff(mode);
		if (range <= 0)
			return diff;
		if (upp != null && upp != from)
			diff += upp.recdiff(this, mode, range - 1);
		if (upm != null && upm != from)
			diff += upm.recdiff(this, mode, range - 1);
		if (samep != null && samep != from)
			diff += samep.recdiff(this, mode, range - 1);
		if (samem != null && samem != from)
			diff += samem.recdiff(this, mode, range - 1);
		return diff;
	}

	private int recifup(int ny, Pixel from, int mode, int range) {
		int diff = ifup(ny, mode);
		if (range <= 0)
			return diff;
		if (upp != null && upp != from)
			diff += upp.recifup(upp.nexty(ny), this, mode, range - 1);
		if (upm != null && upm != from)
			diff += upm.recifup(upm.nexty(ny), this, mode, range - 1);
		if (samep != null && samep != from)
			diff += samep.recifup(ny, this, mode, range - 1);
		if (samem != null && samem != from)
			diff += samem.recifup(ny, this, mode, range - 1);
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
		}
		try {
			this.sidep = map[x + 1][z];
		} catch (IndexOutOfBoundsException e) {
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

	public void verify() throws Exception {
		if ((upp == null || upp.y > y) && (upm == null || upm.y > y) && (downp == null || downp.y < y)
				&& (downm == null || downm.y < y) && (samep == null || samep.y == y)
				&& (samem == null || samem.y == y)) {
			// ok
		} else {
			throw new Exception(this.toString());
		}
	}

	public boolean needunder(Set<Integer> underneeds) {
		return underneeds.contains(id);
	}

	@Override
	public String toString() {
		return String.format("(%d, %d, %d)  upp:%d  upm:%d  downp:%d  downm:%d  samep:%d  samem:%d  sidep:%d  sidem:%d",
				x, y, z, upp != null ? upp.y : null, upm != null ? upm.y : null, downp != null ? downp.y : null,
				downm != null ? downm.y : null, samep != null ? samep.y : null, samem != null ? samem.y : null,
				sidep != null ? sidep.y : null, sidem != null ? sidem.y : null);
	}
}
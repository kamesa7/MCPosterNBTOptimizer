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

	void recconnect(int connectkey) {
		if (connected(connectkey))
			return;
		connect = connectkey;
		if (upp != null && upp.y == y + 1)
			upp.recconnect(connectkey);
		if (upm != null && upm.y == y + 1)
			upm.recconnect(connectkey);
		if (downp != null && downp.y == y - 1)
			downp.recconnect(connectkey);
		if (downm != null && downm.y == y - 1)
			downm.recconnect(connectkey);
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
		else if (upp != null && upp.y == y + 1 && upp.connected(connectkey))
			recconnect(connectkey);
		else if (upm != null && upm.y == y + 1 && upm.connected(connectkey))
			recconnect(connectkey);
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

	Pixel(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.defy = y;
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
		if ((upp == null || upp.y >= y + 1) && (upm == null || upm.y >= y + 1) && (downp == null || downp.y <= y - 1)
				&& (downm == null || downm.y <= y - 1) && (samep == null || samep.y == y)
				&& (samem == null || samem.y == y)) {
		} else {
			throw new Exception(this.toString());
		}
	}

	@Override
	public String toString() {
		return String.format("(%d, %d, %d)  upp:%d  upm:%d  downp:%d  downm:%d  samep:%d  samem:%d  sidep:%d  sidem:%d",
				x, y, z, upp != null ? upp.y : null, upm != null ? upm.y : null, downp != null ? downp.y : null,
				downm != null ? downm.y : null, samep != null ? samep.y : null, samem != null ? samem.y : null,
				sidep != null ? sidep.y : null, sidem != null ? sidem.y : null);
	}

	public int getY() {
		return y;
	}
}
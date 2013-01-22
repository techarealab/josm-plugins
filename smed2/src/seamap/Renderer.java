/* Copyright 2013 Malcolm Herring
 *
 * This is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * For a copy of the GNU General Public License, see <http://www.gnu.org/licenses/>.
 */

package seamap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;

import s57.S57att.Att;
import s57.S57obj.Obj;
import s57.S57val.*;
import s57.S57val;
import seamap.SeaMap;
import seamap.SeaMap.*;
import symbols.Symbols;
import symbols.Symbols.*;

public class Renderer {
	
	static MapHelper helper;
	static SeaMap map;
	static double sScale;
	static double tScale;
	static Graphics2D g2;
	static int zoom;
	
	public static void reRender(Graphics2D g, int z, double factor, SeaMap m, MapHelper h) {
		g2 = g;
		zoom = z;
		helper = h;
		map = m;
		sScale = Symbols.symbolScale[zoom]*factor;
		tScale = Symbols.textScale[zoom]*factor;
		if (map != null) {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
			Rules.rules(map, zoom);
		}
	}
	
	public static AttMap getAtts(Feature feature, Obj obj, int idx) {
		HashMap<Integer, AttMap> objs = feature.objs.get(obj);
		if (objs == null) return null;
		else return objs.get(idx);
	}
	
	public static Object getAttVal(Feature feature, Obj obj, int idx, Att att) {
		AttMap atts = getAtts(feature, obj, idx);
		if (atts == null) return S57val.nullVal(att);
		else {
			AttItem item = atts.get(att);
			if (item == null) return S57val.nullVal(att);
			return item.val;
		}
	}

	public static double calcArea(Feature feature) {
	  if (feature.flag == Fflag.AREA) {
			ArrayList<Long> way;
			if (map.mpolys.containsKey(feature.refs)) {
				way = map.ways.get(map.mpolys.get(feature.refs));
			} else {
				way = map.ways.get(feature.refs);
			}
			Coord coord = map.nodes.get(way.get(0));
	    double llon = coord.lon;
	    double llat = coord.lat;
	    double sigma = 0.0;
			for (long node : way) {
				coord = map.nodes.get(node);
				double lat = coord.lat;
				double lon = coord.lon;
				sigma += (lon * Math.sin(llat)) - (llon * Math.sin(lat));
				llon = lon;
				llat = lat;
	    }
	    return Math.abs(sigma) * 3444 * 3444 / 2.0;
	  }
	  return 0.0;
	}

	public static Coord findCentroid(Feature feature) {
		Coord coord;
		ArrayList<Long> way;
		if (map.mpolys.containsKey(feature.refs)) {
			way = map.ways.get(map.mpolys.get(feature.refs).get(0));
		} else {
			way = map.ways.get(feature.refs);
		}
		switch (feature.flag) {
		case NODE:
			return map.nodes.get(feature.refs);
		case LINE:
			coord = map.nodes.get(way.get(1));
			break;
		case AREA:
		default:
			coord = map.nodes.get(way.get(0));
		}
    double slat = 0.0;
    double slon = 0.0;
    double sarc = 0.0;
    double llat = coord.lat;
    double llon = coord.lon;
		for (long node : way) {
			coord = map.nodes.get(node);
      double lon = coord.lon;
      double lat = coord.lat;
      double arc = (Math.acos(Math.cos(lon-llon) * Math.cos(lat-llat)));
      slat += (lat * arc);
      slon += (lon * arc);
      sarc += arc;
      llat = lat;
      llon = lon;
		}
		return map.new Coord((sarc > 0.0 ? slat/sarc : 0.0), (sarc > 0.0 ? slon/sarc : 0.0));
	}
	
	public static void symbol(Feature feature, Symbol symbol, Obj obj, Delta delta) {
		Point2D point = helper.getPoint(findCentroid(feature));
		if (obj == null) {
			Symbols.drawSymbol(g2, symbol, sScale, point.getX(), point.getY(), delta, null);
		} else {
			ArrayList<ColCOL> colours = (ArrayList<ColCOL>) getAttVal(feature, obj, 0, Att.COLOUR);
			ArrayList<ColPAT> pattern = (ArrayList<ColPAT>) getAttVal(feature, obj, 0, Att.COLPAT);
			Symbols.drawSymbol(g2, symbol, sScale, point.getX(), point.getY(), delta, new Scheme(pattern, colours));
		}
	}
	
	private static Rectangle symbolSize(Symbol symbol) {
		Symbol ssymb = symbol;
		while (ssymb != null) {
			for (Instr item : symbol) {
				if (item.type == Prim.BBOX) {
					return (Rectangle) item.params;
				}
				if (item.type == Prim.SYMB) {
					ssymb = ((SubSymbol)item.params).instr;
					break;
				}
			}
			if (ssymb == symbol)
				break;
		}
		return null;
	}
	
	public static void lineSymbols(Feature feature, Symbol prisymb, double space, Symbol secsymb, int ratio) {
		if (feature.flag != Fflag.NODE) {
			Rectangle prect = symbolSize(prisymb);
			Rectangle srect = symbolSize(secsymb);
			if (srect == null)
				ratio = 0;
			if (prect != null) {
				ArrayList<Long> ways = new ArrayList<Long>();
				double psize = Math.abs(prect.getY()) * sScale;
				double ssize = (srect != null) ? Math.abs(srect.getY()) * sScale : 0;
				if (map.outers.containsKey(feature.refs)) {
					ways.addAll(map.mpolys.get(map.outers.get(feature.refs)));
				} else {
					if (map.mpolys.containsKey(feature.refs)) {
						ways.addAll(map.mpolys.get(feature.refs));
					} else {
						ways.add(feature.refs);
					}
				}
				Point2D prev = new Point2D.Double();
				Point2D next = new Point2D.Double();
				Point2D curr = new Point2D.Double();
				Point2D succ = new Point2D.Double();
				boolean gap = true;
				boolean piv = false;
				double len = 0;
				double angle = 0;
				int scount = ratio;
				Symbol symbol = prisymb;
				for (long way : ways) {
					boolean first = true;
					for (long node : map.ways.get(way)) {
						prev = next;
						next = helper.getPoint(map.nodes.get(node));
						angle = Math.atan2(next.getY() - prev.getY(), next.getX() - prev.getX());
						piv = true;
						if (first) {
							curr = succ = next;
							gap  = (space > 0);
							scount  = ratio;
							symbol  = prisymb;
							len = gap ? psize * space * 0.5 : psize;
							first = false;
						} else {
							while (curr.distance(next) >= len) {
								if (piv) {
									double rem = len;
									double s = prev.distance(next);
									double p = curr.distance(prev);
									if ((s > 0) && (p > 0)) {
										double n = curr.distance(next);
										double theta = Math.acos((s * s + p * p - n * n) / 2 / s / p);
										double phi = Math.asin(p / len * Math.sin(theta));
										rem = len * Math.sin(Math.PI - theta - phi) / Math.sin(theta);
									}
									succ = new Point2D.Double(prev.getX() + (rem * Math.cos(angle)), prev.getY() + (rem * Math.sin(angle)));
									piv = false;
								} else {
									succ = new Point2D.Double(curr.getX() + (len * Math.cos(angle)), curr.getY() + (len * Math.sin(angle)));
								}
								if (!gap) {
									Symbols.drawSymbol(g2, symbol, sScale, curr.getX(), curr.getY(),
											new Delta(Handle.BC, AffineTransform.getRotateInstance(Math.atan2((succ.getY() - curr.getY()), (succ.getX() - curr.getX()) + Math.toRadians(90)))), null);
								}
								if (space > 0) gap = !gap;
								curr = succ;
				        len = gap ? (psize * space) : (--scount == 0) ? ssize : psize;
				        if (scount == 0) {
				          symbol = secsymb;
				          scount = ratio;
				        } else {
				          symbol = prisymb;
				        }
							}
						}
					}
				}
			}
		}
	}

	public static void lineVector (Feature feature, LineStyle style) {
		if (feature.flag != Fflag.NODE) {
			ArrayList<Long> ways = new ArrayList<Long>();
			if (map.outers.containsKey(feature.refs)) {
				ways.addAll(map.mpolys.get(map.outers.get(feature.refs)));
			} else {
				if (map.mpolys.containsKey(feature.refs)) {
					ways.addAll(map.mpolys.get(feature.refs));
				} else {
					ways.add(feature.refs);
				}
			}
			Path2D.Double p = new Path2D.Double();
			p.setWindingRule(GeneralPath.WIND_EVEN_ODD);
			for (long way : ways) {
				boolean first = true;
				for (long node : map.ways.get(way)) {
					Point2D point = helper.getPoint(map.nodes.get(node));
					if (first) {
						p.moveTo(point.getX(), point.getY());
						first = false;
					} else {
						p.lineTo(point.getX(), point.getY());
					}
				}
			}
			if (style.line != null) {
				if (style.dash != null) {
					float[] dash = new float[style.dash.length];
					System.arraycopy(style.dash, 0, dash, 0, style.dash.length);
					for (int i = 0; i < style.dash.length; i++) {
						dash[i] *= (float) sScale;
					}
					g2.setStroke(new BasicStroke((float) (style.width * sScale), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, dash, 0));
				} else {
					g2.setStroke(new BasicStroke((float) (style.width * sScale), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
				}
				g2.setPaint(style.line);
				g2.draw(p);
			}
			if (style.fill != null) {
				g2.setPaint(style.fill);
				g2.fill(p);
			}
		}
	}
	
	public static void labelText (Feature feature, String str, Font font, Color colour, Delta delta) {
		Symbol label = new Symbol();
		label.add(new Instr(Prim.TEXT, new Caption(str, font, colour, (delta == null) ? new Delta(Handle.CC, null) : delta)));
		Point2D point = helper.getPoint(findCentroid(feature));
		Symbols.drawSymbol(g2, label, tScale, point.getX(), point.getY(), null, null);
	}
	
	public static void lineText (Feature feature, String str, Font font, double offset, double dy) {
		
	}
}
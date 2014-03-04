package ua.stu.view.scpview;


import ua.stu.scplib.attribute.GraphicAttribute;
import ua.stu.scplib.attribute.GraphicAttributeBase;
import ua.stu.scplib.data.DataHandler;
import ua.stu.scplib.tools.PointBuffer;
import and.awt.BasicStroke;
import and.awt.Color;
import and.awt.Stroke;

import and.awt.geom.GeneralPath;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Scroller;
import android.widget.TextView;
import net.pbdavey.awt.AwtView;
import net.pbdavey.awt.Font;
import net.pbdavey.awt.Graphics2D;
import net.pbdavey.awt.RenderingHints;

@SuppressLint("FloatMath")
public class GraphicView extends AwtView {
	// object which holds all required data for drawing
	private DataHandler h = null;
	//class of chanels
	private DrawChanels drawChanels=null;
	// point buffer for Linear [NEW 04.03.2013]
	private PointBuffer pointBuffer = new PointBuffer();
	// window size params
	private int W = 920;
	private int H = 600;
	// scroll window params
	private int SW = 920;
	private int SH = 600;
	// inch constant
	private float duim = (float) 25.4;
	// screen size in dpi
	private int sizeScreen = 126;
	//set of graphic attributes
	private GraphicAttributeBase g;
	//the number of tiles to display per column
	private int nTilesPerColumn = 12;
	//the number of tiles to display per row (if 1, then nTilesPerColumn should == numberOfChannels)
	private int nTilesPerRow = 1;
	//how may pixels to use to represent one millivolt
	private float yPixelsInMillivolts;
	//how may pixels to use to represent one millisecond
	private float xPixelsInMilliseconds;
	//how much of the sample data to skip, specified in milliseconds from the start of the samples
	private float timeOffsetInMilliSeconds;
	//offset for graphic
	//private float xTitlesOffset =2* sizeScreen/3;
	// color map
	Color curveColor = Color.blue;
	// basic font
	Font font = null;
	// array for channel offsets
	private float offsets[] = new float[12];
	// any info?
	//переменны для вывода в строку состояния
	private double speed=0;
	private double gain=0;
	private int time=0;
	// touch mode [DEFAULT]
	private int touchMode = GestureListener.MODE_BASIC;
	// fill linear rectangle
	private boolean fillRect = false;

	private TextView tvStatus = null;

	private boolean invert = false;

	// scrolling
	private  GestureDetector gestureDetector;	

	private Scroller scroller;
		
	public void initscale(GestureListener mg){
		gestureDetector = new GestureDetector(getContext(),mg);
		scroller = new Scroller(getContext());
	}
	
	public GraphicView(Context context) {

		super(context);
		// init scrollbars
        setHorizontalScrollBarEnabled(true);
        setVerticalScrollBarEnabled(true);
		TypedArray a = context.obtainStyledAttributes(R.styleable.View);
	    initializeScrollbars(a);
	    a.recycle();
	}
	
	
	public GraphicView(Context context, AttributeSet attribSet) {
		super(context, attribSet);
		// init scrollbars
        setHorizontalScrollBarEnabled(true);
        setVerticalScrollBarEnabled(true);
        TypedArray a = context.obtainStyledAttributes(R.styleable.View);
        initializeScrollbars(a);
        a.recycle();

	}
	
	private static double[] SPEEDS 		= {12.5,25,50,100};
	private static double[] VOLTS 		= {2.5,5,10,20};
	
	private int indexX;
	private int indexY;
	
	private static final int NONE = -1;
	private static final int DRAG = 1;
	private static final int ZOOM = 2;
	private static final int EndZOOM = 3;	
	private static final int UP 	= 5;
	private static final int DOWN	= 6;
	private static final int CLICK = 10;
	
	private float oldDist;
	private int mode;
	
	@Override
	public boolean dispatchTouchEvent (MotionEvent event) {
		int actionMask = event.getActionMasked();
		// check touchMode
		switch (this.touchMode) {
			case GestureListener.MODE_BASIC:
				switch (actionMask) {
				case MotionEvent.ACTION_POINTER_DOWN:
					oldDist = spacing(event);
					if (oldDist > 10f) {
						mode = ZOOM;
					}
					break;
				case MotionEvent.ACTION_DOWN:
					mode = DRAG;
					break;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_POINTER_UP:
					mode = NONE;
					break;
				case MotionEvent.ACTION_MOVE:
					if (mode == DRAG) {
						if (!scroller.isFinished()) scroller.abortAnimation();
					}
					else if (mode == ZOOM) {
						float newDist = spacing(event);
						if (newDist > 10f) {
							float scale = newDist / oldDist;
							if(this.ZoomIt(scale)){
								invalidate();
								mode=EndZOOM;
							}
						}
					break;
					}
				}
				if( (mode ==DRAG)||(mode==NONE) )
					gestureDetector.onTouchEvent(event);
				break;
			case GestureListener.MODE_LINEAR:
				switch(actionMask) {
				// click
				case MotionEvent.ACTION_DOWN:
					mode = CLICK;
					break;
				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_POINTER_UP:
					if (mode == CLICK) {
						boolean full = pointBuffer.push(event.getX(), event.getY());
						// change fill rectangle flag
						if ((!full) && (pointBuffer.insideRect(event.getX(), event.getY()))) {
							if (fillRect == true) fillRect = false;
							else fillRect = true;
						}
						if (pointBuffer.isFull()) invalidate();
					}
					break;
				case MotionEvent.ACTION_MOVE:
					mode = NONE;
					boolean move = pointBuffer.move(event.getX(), event.getY());
					// redraw while moving
					if (move) invalidate();
					break;
				}
				break;
		}
		return true;
	}
	
	private boolean ZoomIt(float Zoom){
		if(Zoom>1.2){
			double y = nextY(DOWN);
			double x = nextX(UP);
			
			reDrawY(y);
			reDrawX(x);
			return true;
		}
		else if(Zoom<0.8){
			double x = nextX(DOWN);
			double y = nextY(UP);

			reDrawX(x);
			reDrawY(y);
			return true;
		}
		return false;
	}
	
	private float spacing(MotionEvent event) {
		   float x = event.getX(0) - event.getX(1);
		   float y = event.getY(0) - event.getY(1);
		   return FloatMath.sqrt(x * x + y * y);
	}
	
	private double nextY ( int mode ) {
		double out = NONE;
		indexY = getIndexY(gain);
		if ( mode == UP ) {
			for (int i = 0; i < VOLTS.length; i++) {
				if ( VOLTS[i] > gain ) {
					out = VOLTS[i];
					break;
				}
			}
		}
		else if ( mode == DOWN ) {
			if ( (indexY - 1) != NONE ) {
				out = VOLTS[indexY - 1];
			}
			else {
				out = VOLTS[0];
			}
		}
		return out;
	}
	
	private double nextX ( int mode ) {
		double out = NONE;
		indexX = getIndexX(speed);
		if ( mode == UP ) {
			for (int i = 0; i < VOLTS.length; i++) {
				if ( SPEEDS[i] > speed ) {
					out = SPEEDS[i];
					break;
				}
			}
		}
		else if ( mode == DOWN ) {
			if ( (indexX - 1) != NONE ) {
				out = SPEEDS[indexX - 1];
			}
			else {
				out = SPEEDS[0];
			}
		}
		return out;
	}
	
	private void reDrawY( double y ) {
		if ( y != NONE ) {
			this.setYScale((float)( y ));
		}
	}
	
	private void reDrawX( double x ) {
		if ( x != NONE ) {
			this.setXScale((float)( x ));
		}
	}
	
	private int getIndexX( double value ) {
		int index = NONE;
		for (int i = 0; i < SPEEDS.length; i++) {
			if ( SPEEDS[i] == value ) {
				index = i;
			}
		}
		return index;
	}

	private int getIndexY( double value ) {
		int index = NONE;
		for (int i = 0; i < VOLTS.length; i++) {
			if ( VOLTS[i] == value ) {
				index = i;
			}
		}
		return index;
	}
	
	/*
	 * Initializing
	 */

	@Override
	public void init() {
		if (h!=null) {
			g = h.getGraphic();
			if (drawChanels!=null) drawChanels.setGraphicAttributeBase(g);	
		}
	}

	/**
	 * Drawing
	 */
	
	@Override
	public void paint(Graphics2D g2) {
		// check DataHandler
		if (h == null) return;
		init();
		// check app mode
		switch (this.touchMode) {
		// basic mode
		case GestureListener.MODE_BASIC:
			// draw ECG
			drawECG(g2);
			break;
		case GestureListener.MODE_LINEAR:
			drawECG(g2);
			if (pointBuffer.isFull()) {
				// drawing rectangle
				g2.setColor(curveColor);
				// save previous options
				Stroke defaultStroke = g2.getStroke();
				Font defaultFont = g2.getFont();
				// set bolder line
				g2.setStroke(new BasicStroke((float) 3));
				GeneralPath thePath = new GeneralPath();
				thePath.reset();
				// make a rectangle
				thePath.moveTo(pointBuffer.getX1(),pointBuffer.getY1());
				thePath.lineTo(pointBuffer.getX2(),pointBuffer.getY1());
				thePath.lineTo(pointBuffer.getX2(),pointBuffer.getY2());
				thePath.lineTo(pointBuffer.getX1(),pointBuffer.getY2());
				thePath.lineTo(pointBuffer.getX1(),pointBuffer.getY1());
				// draw it
				g2.draw(thePath);
				// get width and height
				String w = String.valueOf((int)pointBuffer.getRectW()) + " px.";
				String h = String.valueOf((int)pointBuffer.getRectH()) + " px.";
				// restore previous options
				g2.setStroke(defaultStroke);
				g2.setFont(defaultFont);
				// draw text
				g2.drawString(w, pointBuffer.getMaxX() + 5, pointBuffer.getMidddleHeight());
				g2.drawString(h, pointBuffer.getMiddleWight() - 20, pointBuffer.getMaxY() + 15);
				// fill rectangle with lines
				if (fillRect == true) drawRect(g2, pointBuffer.getRectTopX(), pointBuffer.getRectTopY(),
							pointBuffer.getRectW(), pointBuffer.getRectH());
			}
			break;
		}
        return;
	}
	
	/*
	 * Draw a rectangle of lines
	 */
	private void drawRect(Graphics2D g, float x, float y,float w, float h) {
		Color c = g.getColor();
		g.setColor(Color.gray);
		// get count of vertical lines
		int freq = 10;
		int cnt = (int)w/freq;
		GeneralPath p = new GeneralPath();
		for (int i=1;i<cnt + 1;i++) {
			p.moveTo(x + i*freq, y);
			p.lineTo(x + i*freq, y + h);
		}
		g.draw(p);
		g.setColor(c);
	}

	/**
	 * Basic ECG draw
	 * 
	 */
	private void drawECG(Graphics2D g2) {
		font=new Font("Ubuntu",0,(14));
		float widthOfTileInPixels = getW()/nTilesPerRow;
		float widthOfTileInMilliSeconds = widthOfTileInPixels/xPixelsInMilliseconds;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);	// ugly without
		
		g2.setColor(curveColor);
		g2.setStroke(new BasicStroke((float) 0.7));

		float widthOfSampleInPixels=g.getSamplingIntervalInMilliSeconds()*xPixelsInMilliseconds;
		int timeOffsetInSamples = (int)(timeOffsetInMilliSeconds/g.getSamplingIntervalInMilliSeconds());
		int widthOfTileInSamples = (int)(widthOfTileInMilliSeconds/g.getSamplingIntervalInMilliSeconds());
		int usableSamples = g.getNumberOfSamplesPerChannel()-timeOffsetInSamples;

		if (usableSamples <= 0) {/*usableSamples=0;*/return;}
		
		else if (usableSamples > widthOfTileInSamples) {
			usableSamples=widthOfTileInSamples-1;
		}
		//bound between channels
        float drawingOffsetY = 0;
        //offset for channel middle line
        float currentYChannelOffset = 0;
        float maxY,minY;
        int channel = 0;
        GeneralPath thePath = new GeneralPath();
        //calculating the ideal title offset
        float tile = yPixelsInMillivolts*duim/sizeScreen;
        float yIdealTiles = 4*tile - (int)(tile/2);
        // offset from the top of the screen
        float beforeY = 10;
        // float between names of channels
        float borderBetweenChannelNames = 15;
        // main cycle
        for (int row=0;row<nTilesPerColumn && channel<g.getNumberOfChannels();++row) {
                //getting the maximum and minimum values of Y offset
                maxY = -9999;
                minY = 9999;
                int k = timeOffsetInSamples;
                float curY = 0;
                short[] currenSamplesForThisChannel = g.getSamples()[g.getDisplaySequence()[channel]];
                float currentRescaleY = g.getAmplitudeScalingFactorInMilliVolts()[g.getDisplaySequence()[channel]]*yPixelsInMillivolts;
                // getting the width of channel
                for (int j=1;j<usableSamples;++j) {
                        if (invert) curY = currenSamplesForThisChannel[k]*currentRescaleY;
                        else curY = - currenSamplesForThisChannel[k]*currentRescaleY;
                        if (curY<minY) minY = curY;
                        if (curY>maxY) maxY = curY;
                        k++;
                }
                // Calculating offset Y for current channel
                currentYChannelOffset = drawingOffsetY - minY;
                for (int col=0;col<nTilesPerRow && channel<g.getNumberOfChannels();++col) {
                        short[] samplesForThisChannel = g.getSamples()[g.getDisplaySequence()[channel]];                        
                        int i = timeOffsetInSamples;
                        // YScale attribute
                        float rescaleY = g.getAmplitudeScalingFactorInMilliVolts()[g.getDisplaySequence()[channel]]*yPixelsInMillivolts;
                        float fromXValue = 0;
                        float fromYValue;
                        if (invert) fromYValue = currentYChannelOffset + samplesForThisChannel[i]*rescaleY;
                        else fromYValue = currentYChannelOffset - samplesForThisChannel[i]*rescaleY;
                        // value for auto-trimming
                        float delta = 0;
                        if (fromYValue > beforeY + (maxY - minY)) delta = beforeY + (maxY - minY) - fromYValue +20;
                        if (fromYValue < beforeY + yIdealTiles + borderBetweenChannelNames) delta = beforeY + yIdealTiles - fromYValue + borderBetweenChannelNames;
                        beforeY = fromYValue + delta;
                        if (col == 0) offsets[row] = fromYValue + delta;
                        thePath.reset();
                        thePath.moveTo(fromXValue,fromYValue + delta);
                        ++i;
                        for (int j=1;j<usableSamples;++j) {
                                float toXValue = fromXValue + widthOfSampleInPixels;
                                float toYValue;
                                if (invert) toYValue = currentYChannelOffset + samplesForThisChannel[i]*rescaleY + delta;
                                else toYValue = currentYChannelOffset - samplesForThisChannel[i]*rescaleY + delta;                                        
                                i++;
                                if ((int)fromXValue != (int)toXValue || (int)fromYValue != (int)toYValue) {
                                        thePath.lineTo(toXValue,toYValue);
                                }
                                fromXValue=toXValue;
                                fromYValue=toYValue;
                        }
                        g2.draw(thePath);
                        ++channel;
                }
                // calculating bound between channels
                drawingOffsetY = (currentYChannelOffset + maxY);
        }
        currentYChannelOffset = 0;
        // update offsets for channels, and redraw them
        if (drawChanels!=null) {
                drawChanels.setOffsets(offsets);
                drawChanels.invalidate();
        }
	}
	
	/**
	 * Setters & getters
	 */
	
	public GraphicAttributeBase getG() {
		return g;
	}

	public void setG(GraphicAttributeBase g) {
		this.g = g;
	}

	public int getnTilesPerColumn() {
		return nTilesPerColumn;
	}

	public void setnTilesPerColumn(int nTilesPerColumn) {
		this.nTilesPerColumn = nTilesPerColumn;
	}

	public int getnTilesPerRow() {
		return nTilesPerRow;
	}

	public void setnTilesPerRow(int nTilesPerRow) {
		this.nTilesPerRow = nTilesPerRow;
	}

	public float getyPixelsInMillivolts() {
		return yPixelsInMillivolts;
	}

	public void setyPixelsInMillivolts(int yPixelsInMillivolts) {
		Log.d("setyPixelsInMillivolts",Integer.toString(yPixelsInMillivolts));
		this.yPixelsInMillivolts = yPixelsInMillivolts;
		if (drawChanels!=null){
			drawChanels.setyPixelsInMillivolts(yPixelsInMillivolts);
			drawChanels.invalidate();
		}
	}

	public float getxPixelsInMilliseconds() {
		return xPixelsInMilliseconds;
	}

	public void setxPixelsInMilliseconds(int xPixelsInMilliseconds) {
		Log.d("setxPixelsInMilliseconds",Integer.toString(xPixelsInMilliseconds));
		this.xPixelsInMilliseconds = xPixelsInMilliseconds;
	}

	public float getTimeOffsetInMilliSeconds() {
		return timeOffsetInMilliSeconds;
	}

	public void setTimeOffsetInMilliSeconds(float timeOffsetInMilliSeconds) {
		this.timeOffsetInMilliSeconds = timeOffsetInMilliSeconds;
	}
	
	public void setFont(Font font) {
		this.font = font;
	}
	
	public void setH(DataHandler h) {
		this.h = h;
	}
	
	public void setSW(int sW) {
		SW = sW;
	}

	public void setSH(int sH) {
		SH =sH;
	}
	public int getSW() {
		return  (SW);
	}

	public int getSH() {
		return (SH);
	}
	public int getW() {
		return (W);
	}

	public void setW(int w) {
		W = w;
	}

	public int getH() {
		return H;
	}
	public void setFont(String fontName) {
		this.font = new Font(fontName,0,14);
	}
	
	void setGraphicParameters(GraphicAttribute g) {
		this.g = g;
	}
	
	public void setYScale(float santimetersPerMillivolt) {
		float millimetersPerMillivolt=santimetersPerMillivolt/10;
		this.yPixelsInMillivolts = (7/millimetersPerMillivolt/(duim/sizeScreen));
		if (drawChanels!=null){
		drawChanels.setYScale(millimetersPerMillivolt);
		drawChanels.invalidate();
		this.gain=santimetersPerMillivolt;		
		}	
		if (tvStatus!=null && this.h!=null)
			tvStatus.setText(time+" from start "+speed+" mm/sec. "+gain+" mV/mm");
	}
	
	public void setXScale(float millimetersPerSecond) {
		this.xPixelsInMilliseconds = (float)( (millimetersPerSecond*(3.15/5)/(1000*duim/sizeScreen)));
		this.W=(int) (millimetersPerSecond*32);
		this.SW=(int) ((int)millimetersPerSecond*(32.65)+50);	
		this.speed=millimetersPerSecond;
		if (tvStatus!=null && this.h!=null)
		tvStatus.setText(time+" from start "+speed+" mm/sec. "+gain+" mV/mm");
	
	}
	public void setInvert(boolean invert) {
		this.invert = invert;
		if (drawChanels!=null){
		drawChanels.setInvert(invert);
		drawChanels.invalidate();
		}
	}
	public void setDrawChanels(DrawChanels drawChanels){
		this.drawChanels=drawChanels;
	}
	
	public void setTvStatus(TextView tvStatus){
		this.tvStatus=tvStatus;
	}
	public void setGraphicColor(Color c){
		this.curveColor=c;
	}
	
	public Scroller getScroller() {
		return scroller;
	}
	
	public void setTime(float distanse) {
		if(h!=null){
			if(g.isFlNonsection2()){
		this.time += (int)distanse*(((g.getNumberOfSamplesPerChannel())*2.05/*подстроечный коефициент взят с потолка */)/getW())*2;
			}else{
				this.time += (int)distanse*(((g.getNumberOfSamplesPerChannel())*2.05/*подстроечный коефициент взят с потолка */)/getW());
			}
		if (tvStatus!=null && this.h!=null)
			tvStatus.setText(this.time+" from start "+speed+" mm/sec. "+gain+" mV/mm");
		}
	}	
	public void setTimeInNull() {
			if(h!=null){
			this.time = 0;
			if (tvStatus!=null && this.h!=null)
				tvStatus.setText(this.time+" from start "+speed+" mm/sec. "+gain+" mV/mm");
			}
	}
	
	public void setMode(int mode) {
		this.touchMode = mode;
		if (mode == GestureListener.MODE_BASIC) {
			pointBuffer.clear();
			fillRect = false;
		}
	}
}
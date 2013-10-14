/*
 * Copyright (C) 2013 たんらる
 */

package fourthline.mmlTools.parser;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import fourthline.mmlTools.core.MMLTools;

public class MMLTrack extends MMLTools {
	private List<List<MMLEvent>> mmlParts;
	private int totalTick[];
	private int program = 0;
	private String name;
	private int panpot = 64;
	
	public MMLTrack(String mml) {
		super(mml);

		mmlParse();
	}
	
	public MMLTrack(String mml1, String mml2, String mml3) {
		super(mml1, mml2, mml3);
		
		mmlParse();
	}
	
	private void mmlParse() {
		mmlParts = new ArrayList<List<MMLEvent>>();

		totalTick = new int[3];
		String mml[] = {
				getMelody(),
				getChord1(),
				getChord2()
		};
		
		for (int i = 0; i < mml.length; i++) {
			parseMMLPart(mml[i], i);
		}
	}
	
	private void parseMMLPart(String mml, int index) {
		MMLEventParser parser = new MMLEventParser("");
		if (index < mmlParts.size()) {
			mmlParts.set( index, parser.parseMML(mml) );
		} else {
			mmlParts.add( parser.parseMML(mml) );
		}
		totalTick[index] = parser.getTotalTick();
	}

	public void setMelody(String mml) {
		parseMMLPart(mml, 0);
		this.mml_melody = mml;
	}
	
	public void setChord1(String mml) {
		parseMMLPart(mml, 1);
		this.mml_chord1 = mml;
	}
	
	public void setChord2(String mml) {
		parseMMLPart(mml, 2);
		this.mml_chord2 = mml;
	}

	public void setProgram(int program) {
		this.program = program;
	}

	public int getProgram() {
		return this.program;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return this.name;
	}

	public void setPanpot(int panpot) {
		if (panpot > 127) {
			panpot = 127;
		} else if (panpot < 0) {
			panpot = 0;
		}
		this.panpot = panpot;
	}
	
	public int getPanpot() {
		return this.panpot;
	}
	
	public List<MMLEvent> getMMLEvent(int index) {
		return mmlParts.get(index);
	}
	
	public int getMaxTickLength() {
		int max = 0;
		for (int i = 0; i < totalTick.length; i++) {
			if (max < totalTick[i]) {
				max = totalTick[i];
			}
		}
		
		return max;
	}

	private int convertVelocityMML2Midi(int mml_velocity) {
		return (mml_velocity * 8);
	}
	private int convertNoteMML2Midi(int mml_note) {
		return (mml_note + 12);
	}

	public void convertMidiTrack(Track track, int channel) throws InvalidMidiDataException {
		ShortMessage message = new ShortMessage(ShortMessage.PROGRAM_CHANGE, 
				channel,
				program,
				0);
		track.add(new MidiEvent(message, 0));

		/* ctrl 10 パンポット */
		message = new ShortMessage(ShortMessage.CONTROL_CHANGE, 
				channel,
				10,
				panpot);
		track.add(new MidiEvent(message, 0));

		convertMidiTrack_part(track, channel, 0);
		convertMidiTrack_part(track, channel, 1);
		convertMidiTrack_part(track, channel, 2);
	}

	protected void convertMidiTrack_part(Track track, int channel, int index) throws InvalidMidiDataException {
		int totalTick = 0;
		int velocity = 8;

		List<MMLEvent> part = mmlParts.get(index);

		for ( Iterator<MMLEvent> i = part.iterator(); i.hasNext(); ) {
			MMLEvent event = i.next();
			System.out.println(" <mml-midi> " + event.toString());

			if (event instanceof MMLNoteEvent) {
				int note = ((MMLNoteEvent) event).getNote();
				int tick = ((MMLNoteEvent) event).getTick();

				if (note >= 0) {
					// ON イベント作成
					MidiMessage message1 = new ShortMessage(ShortMessage.NOTE_ON, 
							channel,
							convertNoteMML2Midi(note), 
							convertVelocityMML2Midi(velocity));
					track.add(new MidiEvent(message1, totalTick));

					// Off イベント作成
					MidiMessage message2 = new ShortMessage(ShortMessage.NOTE_OFF,
							channel, 
							convertNoteMML2Midi(note),
							0);
					track.add(new MidiEvent(message2, totalTick+tick-1));
				}

				totalTick += tick;
			} else if (event instanceof MMLVelocityEvent) {
				velocity = ((MMLVelocityEvent) event).getVelocity();
			} else if (event instanceof MMLTempoEvent) {
				byte tempo[] = ((MMLTempoEvent) event).getMetaData();
				MidiMessage message = new MetaMessage(MMLTempoEvent.META, 
						tempo, tempo.length);
				track.add(new MidiEvent(message, totalTick));
			}
		}
	}
}

/*
 * Copyright (C) 2013 たんらる
 */

package fourthline.mabiicco.ui;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Track;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;

import fourthline.mabiicco.midi.MabiDLS;
import fourthline.mmlTools.core.MMLTools;
import fourthline.mmlTools.parser.MMLTempoEvent;
import fourthline.mmlTools.parser.MMLTrack;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;

public class MMLSeqView extends JPanel implements INotifyMMLTrackProperty {

	/**
	 * 
	 */
	private static final long serialVersionUID = -479890612015524747L;

	private static final int MAX_TRACK = 8;

	private JScrollPane scrollPane;
	private PianoRollView pianoRollView;
	private KeyboardView keyboardView;
	private JTabbedPane tabbedPane;

	private MMLTrack track[];


	/**
	 * Create the panel.
	 */
	public MMLSeqView() {
		setLayout(new BorderLayout(0, 0));

		// Scroll View (KeyboardView, PianoRollView) - CENTER
		pianoRollView = new PianoRollView();
		keyboardView = new KeyboardView();

		scrollPane = new JScrollPane(pianoRollView);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);

		scrollPane.setRowHeaderView(keyboardView);
		JPanel columnView = pianoRollView.getRulerPanel();
		columnView.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int x = e.getX();
				Sequencer sequencer = MabiDLS.getInstance().getSequencer();
				if (!sequencer.isRunning()) {
					pianoRollView.setSequenceX(x);
				} else {
					long tick = pianoRollView.convertXtoTick(x);
					Sequence sequence = sequencer.getSequence();
					// 移動先のテンポに設定する.
					int tempo = getTempoInSequenceAtTick(sequence, tick);
					sequencer.setTickPosition(tick);
					sequencer.setTempoInBPM(tempo);
				}
			}
		});
		scrollPane.setColumnHeaderView(columnView);

		add(scrollPane, BorderLayout.CENTER);
		pianoRollView.setViewportAndParent(scrollPane.getViewport(), this);


		// MMLTrackView (tab) - SOUTH
		tabbedPane = new JTabbedPane(JTabbedPane.TOP);

		tabbedPane.setPreferredSize(new Dimension(0, 180));
		add(tabbedPane, BorderLayout.SOUTH);

		initialSetView();
		initializeMMLTrack();
	}

	private void initialSetView() {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				// (ピアノロール全体の高さ / 2) - （表示領域 / 2）＝真ん中の座標。
				int y = (pianoRollView.getHeight() / 2) - (scrollPane.getHeight() / 2);

				// 初期のView位置
				scrollPane.getViewport().setViewPosition(new Point(0, y));
			}
		});
	}



	public void initializeMMLTrack() {
		MMLTrack newTrack[] = new MMLTrack[5];
		for (int i = 0; i < 5; i++) {
			String name = "Track"+(i+1);
			newTrack[i] = new MMLTrack("");
			newTrack[i].setName(name);
		}

		setMMLTracks(newTrack);
	}


	/**
	 * トラックの追加
	 */
	public void addMMLTrack(String title) {
		addMMLTrack(title, "");
	}


	/**
	 * トラックの追加, MML書式
	 */
	private void addMMLTrack(String title, String mml) {
		MMLTrack newTrack = new MMLTrack(mml);
		newTrack.setName(title);
		if (track.length >= MAX_TRACK) {
			return;
		}

		ArrayList<MMLTrack> list = new ArrayList<MMLTrack>( Arrays.asList(track) );
		list.add(newTrack);

		track = new MMLTrack[list.size()];
		list.toArray(track);

		// トラックの追加
		tabbedPane.add(title, new MMLTrackView(newTrack, track.length-1));
		tabbedPane.setSelectedIndex(track.length-1);

		// ピアノロール更新
		pianoRollView.setMMLTrack(track);
	}


	/**
	 * トラックの削除
	 * 現在選択中のトラックを削除します。
	 */
	public void removeMMLTrack() {
		int index = tabbedPane.getSelectedIndex();
		ArrayList<MMLTrack> list = new ArrayList<MMLTrack>( Arrays.asList(track) );
		list.remove(index);
		tabbedPane.remove(index);
		track = new MMLTrack[list.size()];
		track = list.toArray(track);

		// MMLTrackViewのチャンネルを更新する.
		for (int i = index; i < tabbedPane.getComponentCount(); i++) {
			MMLTrackView view = (MMLTrackView) (tabbedPane.getComponentAt(i));
			view.setChannel(i);
		}

		// ピアノロール更新
		pianoRollView.setMMLTrack(track);
	}


	/**
	 * MIDIシーケンスを作成します。
	 * @throws InvalidMidiDataException 
	 */
	private Sequence createSequence() throws InvalidMidiDataException {
		Sequence sequence = new Sequence(Sequence.PPQ, 96);

		for (int i = 0; i < track.length; i++) {
			MMLTrack mmlTrack = track[i];
			mmlTrack.convertMidiTrack(sequence.createTrack(), i);
		}

		return sequence;
	}
	
	/**
	 * 全トラックにおける、指定Tick位置のテンポを取得する。
	 */
	private int getTempoInSequenceAtTick(Sequence sequence, long tick) {
		Track midiTrack[] = sequence.getTracks();
		long searchTick = 0;
		int tempoOfTick = 120;
		
		for (int i = 0; i < midiTrack.length; i++) {
			int eventCount = midiTrack[i].size();
			
			for (int j = 0; j < eventCount; j++) {
				MidiEvent e = midiTrack[i].get(j);
				MidiMessage message = e.getMessage();
				long messageTick = e.getTick();
				
				if (message instanceof MetaMessage) {
					if ( ((MetaMessage) message).getType() == MMLTempoEvent.META) {
						byte b[] = ((MetaMessage) message).getData();
						int tempo = ((int)b[0])&0xff;
						if ( (searchTick <= messageTick) &&  (messageTick < tick) ) {
							tempoOfTick = tempo;
							searchTick = messageTick;
						}
					}
				}
			}
		}
		
		return tempoOfTick;
	}
	
	/**
	 * 再生スタート（現在のシーケンス位置を使用）
	 */
	public void startSequence() {
		new Thread(new Runnable() {
			public void run() {
				try {
					Sequencer sequencer = MabiDLS.getInstance().getSequencer();
					Sequence sequence = createSequence();
					
					// 再生開始が先頭でない場合、そこのテンポに設定する必要がある。
					long startTick = pianoRollView.getSequencePossition();
					int tempo = getTempoInSequenceAtTick(sequence, startTick);
					System.out.printf("Sequence start: tick(%d), tempo(%d)\n", startTick, tempo);
					sequencer.setSequence(sequence);
					sequencer.setTickPosition(startTick);
					sequencer.setTempoInBPM(tempo);
					sequencer.start();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}


	/**
	 * 新規で複数のトラックをセットする。
	 */
	public void setMMLTracks(MMLTrack track[]) {
		if (track.length > MAX_TRACK) {
			return;
		}

		tabbedPane.removeAll();
		pianoRollView.setMMLTrack(track);
		pianoRollView.repaint();
		this.track = track;

		for (int i = 0; i < track.length; i++) {
			String name = track[i].getName();
			if (name == null) {
				name = "Track"+(i+1);
			}

			tabbedPane.add(name, new MMLTrackView(track[i], i));
		}

		initialSetView();
		pianoRollView.setSequenceX(0);
	}

	/**
	 * 現在のトラックにMMLを設定する。
	 */
	public void setMMLselectedTrack(String mml) {
		int index = tabbedPane.getSelectedIndex();

		MMLTrack selectedTrack = track[index];

		MMLTools tools = new MMLTools(mml);
		selectedTrack.setMelody(tools.getMelody());
		selectedTrack.setChord1(tools.getChord1());
		selectedTrack.setChord2(tools.getChord2());

		// 表示を更新
		pianoRollView.setMMLTrack(track);
		MMLTrackView view = (MMLTrackView)tabbedPane.getComponentAt(index);
		view.setMMLTrack(track[index]);
	}

	/**
	 * 現在選択中のトラックを取得する。
	 */

	public MMLTrack getSelectedTrack() {
		int index = tabbedPane.getSelectedIndex();

		if (index < 0) {
			index = 0;
		}

		return track[index];
	}

	@Override
	public void setTrackProperty(MMLTrack track) {
		tabbedPane.setTitleAt(tabbedPane.getSelectedIndex(), track.getName());
	}
	
	private void setViewPosition(int x) {
		JViewport viewport = scrollPane.getViewport();
		Point point = viewport.getViewPosition();

		point.setLocation(x, point.getY());
		viewport.setViewPosition(point);
	}
	
	/**
	 * 表示ライン表示を現在のシーケンス位置に戻す
	 */
	public void resetViewPosition() {
		setViewPosition(pianoRollView.getSequenceX());
	}
	
	/**
	 * シーケンスの現在位置を先頭に戻す
	 */
	public void setStartPosition() {
		Sequencer sequencer = MabiDLS.getInstance().getSequencer();
		if (!sequencer.isRunning()) {
			setViewPosition(0);
			pianoRollView.setSequenceX(0);
		} else {
			sequencer.setTickPosition(0);
			sequencer.setTempoInBPM(120);
		}
	}
}

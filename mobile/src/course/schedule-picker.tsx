import { useRef, useState, type ComponentRef, type PropsWithChildren, type ReactNode } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { alarmTime, minutes, nextTimeSlot, timeAt, timeStep } from './schedule';
import { Button, colors, Copy, Heading } from './ui';

type PickerProps = { value: string; onSelect: (value: string) => void; onClose: () => void };

function Picker({ title, onClose, children, confirm }: PropsWithChildren<{
  title: string; onClose: () => void; confirm?: ReactNode;
}>) {
  return (
    <Modal visible transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={styles.dialog} accessibilityViewIsModal>
          <View style={styles.footer}>
            <Heading>{title}</Heading>
          </View>
          {confirm ? <View style={styles.content}>{children}</View>
            : <ScrollView style={styles.options} contentContainerStyle={styles.content}>{children}</ScrollView>}
          <View style={styles.footer}>
            {confirm}
            <Button title="취소" secondary onPress={onClose} />
          </View>
        </View>
      </View>
    </Modal>
  );
}

const weekdays = ['일', '월', '화', '수', '목', '금', '토'];

export function DatePicker({ value, minimum, onSelect, onClose }: PickerProps & { minimum: string }) {
  const [month, setMonth] = useState(() => new Date((value < minimum ? minimum : value).slice(0, 7) + '-01T00:00:00Z'));
  const year = month.getUTCFullYear();
  const monthIndex = month.getUTCMonth();
  const monthLabel = year + '년 ' + (monthIndex + 1) + '월';
  const canGoBack = month.toISOString().slice(0, 7) > minimum.slice(0, 7);
  const offset = month.getUTCDay();
  const dayCount = new Date(Date.UTC(year, monthIndex + 1, 0)).getUTCDate();

  return (
    <Picker title="방문 날짜 선택" onClose={onClose}>
      <View style={styles.monthHeader}>
        <Pressable accessibilityRole="button" accessibilityLabel="이전 달" disabled={!canGoBack}
          style={styles.monthButton} onPress={() => setMonth(new Date(Date.UTC(year, monthIndex - 1, 1)))}>
          <Text style={[styles.dayText, !canGoBack && styles.disabled]}>‹</Text>
        </Pressable>
        <Heading>{monthLabel}</Heading>
        <Pressable accessibilityRole="button" accessibilityLabel="다음 달" style={styles.monthButton}
          onPress={() => setMonth(new Date(Date.UTC(year, monthIndex + 1, 1)))}>
          <Text style={styles.dayText}>›</Text>
        </Pressable>
      </View>
      <View style={styles.calendar}>
        {weekdays.map(day => <Text key={day} style={[styles.cell, styles.weekday]}>{day}</Text>)}
        {Array.from({ length: offset }, (_, index) => <View key={'blank-' + index} style={styles.cell} />)}
        {Array.from({ length: dayCount }, (_, index) => {
          const day = index + 1;
          const date = new Date(Date.UTC(year, monthIndex, day)).toISOString().slice(0, 10);
          const selected = date === value;
          const disabled = date < minimum;
          return (
            <Pressable key={date} accessibilityRole="button" accessibilityLabel={monthLabel + ' ' + day + '일'}
              accessibilityState={{ selected, disabled }} disabled={disabled} onPress={() => onSelect(date)}
              style={[styles.cell, styles.day, selected && styles.selected]}>
              <Text style={[styles.dayText, disabled && styles.disabled]}>{day}</Text>
            </Pressable>
          );
        })}
      </View>
    </Picker>
  );
}

const wheelRowHeight = 46;

function Wheel({ label, entries, selected, onChange }: {
  label: string; entries: { value: number; label: string }[]; selected: number; onChange: (value: number) => void;
}) {
  const index = entries.findIndex(entry => entry.value === selected);
  const [initialOffset] = useState(() => ({ x: 0, y: index * wheelRowHeight }));
  const scroll = useRef<ComponentRef<typeof ScrollView>>(null);
  const displayedIndex = useRef(index);

  function changeIndex(next: number) {
    const clamped = Math.max(0, Math.min(entries.length - 1, next));
    if (displayedIndex.current !== clamped) {
      displayedIndex.current = clamped;
      onChange(entries[clamped].value);
    }
    return clamped;
  }

  function jump(next: number) {
    const clamped = changeIndex(next);
    scroll.current?.scrollTo({ y: clamped * wheelRowHeight, animated: false });
  }

  return (
    <ScrollView ref={scroll} style={styles.wheel} contentContainerStyle={styles.wheelContent}
      contentOffset={initialOffset} snapToInterval={wheelRowHeight} decelerationRate="fast"
      bounces={false} overScrollMode="never" showsVerticalScrollIndicator={false} scrollEventThrottle={16}
      onScroll={event => changeIndex(Math.round(event.nativeEvent.contentOffset.y / wheelRowHeight))}
      accessible accessibilityRole="adjustable" accessibilityLabel={label} accessibilityValue={{ text: entries[index].label }}
      accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
      onAccessibilityAction={event => jump(index + (event.nativeEvent.actionName === 'increment' ? 1 : -1))}>
      {entries.map((entry, entryIndex) => (
        <Pressable key={entry.value} style={styles.wheelRow} onPress={() => jump(entryIndex)}>
          <Text style={[styles.wheelText, { opacity: [1, 0.5, 0.26][Math.min(2, Math.abs(entryIndex - index))] },
            entryIndex === index && styles.wheelSelected]}>{entry.label}</Text>
        </Pressable>
      ))}
    </ScrollView>
  );
}

export function ClockPicker({ title, value, minimum, maximum, onSelect, onClose }: PickerProps & {
  title: string; minimum: number; maximum: number;
}) {
  const first = nextTimeSlot(minimum);
  const last = Math.floor(maximum / timeStep) * timeStep;
  const [clock, setClock] = useState(() => nextTimeSlot(minutes(value)));
  if (first > last) {
    return <Picker title={title} onClose={onClose}><Copy>선택할 수 있는 시간이 없어요. 방문 날짜를 바꿔 주세요.</Copy></Picker>;
  }
  const bound = (time: number) => Math.max(first, Math.min(time, last));
  const selected = bound(clock);
  const hour = Math.floor(selected / 60);
  const period = Math.floor(hour / 12);
  const firstHour = Math.max(period * 12, Math.floor(first / 60));
  const lastHour = Math.min(period * 12 + 11, Math.floor(last / 60));
  const firstMinute = Math.max(0, first - hour * 60);
  const lastMinute = Math.min(55, last - hour * 60);

  return (
    <Picker title={title} onClose={onClose}
      confirm={<Button title={alarmTime(timeAt(selected)) + ' 완료'} onPress={() => onSelect(timeAt(selected))} />}>
      <View style={styles.wheels}>
        <View pointerEvents="none" style={styles.wheelHighlight} />
        <Wheel label="오전·오후" selected={period} entries={[
          { value: 0, label: '오전' }, { value: 1, label: '오후' },
        ].filter(entry => entry.value * 720 <= last && (entry.value + 1) * 720 > first)}
        onChange={value => setClock(bound((hour % 12 + value * 12) * 60 + selected % 60))} />
        <Wheel key={'hour-' + period} label="시" selected={hour} entries={
          Array.from({ length: lastHour - firstHour + 1 }, (_, index) => {
            const value = firstHour + index;
            return { value, label: String(value % 12 || 12) };
          })
        } onChange={value => setClock(bound(value * 60 + selected % 60))} />
        <Wheel key={'minute-' + hour} label="분" selected={selected % 60} entries={
          Array.from({ length: (lastMinute - firstMinute) / timeStep + 1 }, (_, index) => {
            const value = firstMinute + index * timeStep;
            return { value, label: String(value).padStart(2, '0') };
          })
        } onChange={value => setClock(hour * 60 + value)} />
      </View>
    </Picker>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, padding: 16, alignItems: 'center', justifyContent: 'center', backgroundColor: '#11223c80' },
  dialog: { width: '100%', maxWidth: 460, maxHeight: '90%', borderRadius: 18, backgroundColor: colors.surface },
  options: { flexGrow: 0, flexShrink: 1 },
  content: { paddingHorizontal: 16, gap: 12 },
  footer: { padding: 16, gap: 8 },
  monthHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  monthButton: { minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  calendar: { flexDirection: 'row', flexWrap: 'wrap' },
  cell: { width: '14.28%' },
  weekday: { color: colors.muted, textAlign: 'center', paddingVertical: 6 },
  day: { minHeight: 44, alignItems: 'center', justifyContent: 'center', borderRadius: 10 },
  dayText: { color: colors.text, fontSize: 16, fontWeight: '600' },
  selected: { backgroundColor: colors.pale },
  disabled: { opacity: 0.35 },
  wheels: { flexDirection: 'row', height: wheelRowHeight * 5 },
  wheel: { flex: 1, height: wheelRowHeight * 5 },
  wheelContent: { paddingVertical: wheelRowHeight * 2 },
  wheelHighlight: { position: 'absolute', left: 0, right: 0, top: wheelRowHeight * 2,
    height: wheelRowHeight, borderRadius: 10, backgroundColor: colors.pale },
  wheelRow: { height: wheelRowHeight, justifyContent: 'center', alignItems: 'center' },
  wheelText: { color: colors.text, fontSize: 23, fontWeight: '500', fontVariant: ['tabular-nums'] },
  wheelSelected: { fontWeight: '700' },
});

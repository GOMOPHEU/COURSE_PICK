import { router } from 'expo-router';
import { useRef, useState, type ComponentRef } from 'react';
import type { LayoutChangeEvent, ScrollView } from 'react-native';

import { startPoints, themes, transportLabels, type Theme } from '@/course/contracts';
import { alarmTime, lastEndTime, lastStartTime, minutes, moveStart, nextDate, nextTimeSlot,
  seoulNow, validateFields, type SurveyErrors } from '@/course/schedule';
import { ClockPicker, DatePicker } from '@/course/schedule-picker';
import { useCourseStore } from '@/course/store';
import { Button, Card, ChoiceGroup, Screen, SelectionField } from '@/course/ui';

const themeOptions = (Object.keys(themes) as Theme[]).map(value => ({ value, label: themes[value] }));
const companions = [
  { value: 'solo', label: '혼자' }, { value: 'friends', label: '친구' },
  { value: 'family', label: '가족' }, { value: 'couple', label: '연인' },
] as const;
const ages = [
  { value: '10s', label: '10대' }, { value: '20s', label: '20대' }, { value: '30s', label: '30대' },
  { value: '40s', label: '40대' }, { value: '50plus', label: '50대 이상' },
] as const;

export default function Survey() {
  const request = useCourseStore(state => state.request);
  const update = useCourseStore(state => state.update);
  const [checkedAt, setCheckedAt] = useState<ReturnType<typeof seoulNow> | null>(null);
  const [themeLimitReached, setThemeLimitReached] = useState(false);
  const [showOptional, setShowOptional] = useState(false);
  const [picker, setPicker] = useState<'date' | 'start' | 'end' | null>(null);
  const [now, setNow] = useState(() => seoulNow());
  const scroll = useRef<ComponentRef<typeof ScrollView>>(null);
  const cardOffsets = useRef({ schedule: 0, preferences: 0 });
  const fieldOffsets = useRef<Partial<Record<keyof SurveyErrors, number>>>({});
  const errors = checkedAt ? validateFields(request, checkedAt) : {};
  const currentSlot = nextTimeSlot(minutes(now.time));
  const startFloor = request.date === now.date ? currentSlot : 0;
  const earliestDate = currentSlot > lastStartTime ? nextDate(now.date) : now.date;
  const closePicker = () => setPicker(null);

  function openPicker(field: 'date' | 'start' | 'end') {
    setNow(seoulNow());
    setPicker(field);
  }

  function selectSchedule(change: Partial<typeof request>) {
    update(change);
    if (checkedAt) setCheckedAt(seoulNow());
    closePicker();
  }

  function rememberField(field: keyof SurveyErrors, event: LayoutChangeEvent) {
    fieldOffsets.current[field] = event.nativeEvent.layout.y;
  }

  function confirm() {
    const current = seoulNow();
    setCheckedAt(current);
    const first = (Object.keys(validateFields(request, current)) as (keyof SurveyErrors)[])[0];
    if (!first) {
      router.push('/confirm');
      return;
    }
    const card = first === 'transport' || first === 'themes' ? 'preferences' : 'schedule';
    const y = cardOffsets.current[card] + (fieldOffsets.current[first] ?? 0);
    scroll.current?.scrollTo({ y: Math.max(0, y - 12), animated: true });
  }

  return (
    <Screen title="서울 나들이 코스 찾기" subtitle="출발지와 머무를 시간을 정하고, 가보고 싶은 곳을 골라보세요."
      scrollRef={scroll}>
      <Card onLayout={event => { cardOffsets.current.schedule = event.nativeEvent.layout.y; }}>
        <ChoiceGroup title="출발지" error={errors.startPointId} onLayout={event => rememberField('startPointId', event)}
          options={startPoints.map(point => ({ value: point.id, label: point.name }))}
          selected={[request.startPointId]} onChange={startPointId => update({ startPointId })} />
        <SelectionField label="방문 날짜" value={request.date} error={errors.date} onLayout={event => rememberField('date', event)}
          onPress={() => openPicker('date')} />
        <SelectionField label="출발 시각" value={alarmTime(request.startTime)} error={errors.startTime}
          onLayout={event => rememberField('startTime', event)} onPress={() => openPicker('start')} />
        <SelectionField label="종료 시각" value={alarmTime(request.endTime)} error={errors.endTime}
          onLayout={event => rememberField('endTime', event)} onPress={() => openPicker('end')} />
      </Card>
      {picker === 'date' && <DatePicker value={request.date} minimum={earliestDate} onClose={closePicker} onSelect={date => {
        const minimum = date === now.date ? currentSlot : 0;
        const start = Math.min(lastStartTime, nextTimeSlot(minutes(request.startTime)));
        selectSchedule({ date, ...moveStart(request, Math.max(minimum, start)) });
      }} />}
      {picker === 'start' && <ClockPicker title="출발 시각 선택" value={request.startTime} minimum={startFloor}
        maximum={lastStartTime} onClose={closePicker} onSelect={time => selectSchedule(moveStart(request, minutes(time)))} />}
      {picker === 'end' && <ClockPicker title="종료 시각 선택" value={request.endTime}
        minimum={Math.max(startFloor, minutes(request.startTime) + 1)} maximum={lastEndTime}
        onClose={closePicker} onSelect={endTime => selectSchedule({ endTime })} />}
      <Card onLayout={event => { cardOffsets.current.preferences = event.nativeEvent.layout.y; }}>
        <ChoiceGroup title="이동수단" error={errors.transport} onLayout={event => rememberField('transport', event)} options={[
          { value: 'walk', label: transportLabels.walk },
          { value: 'transit', label: transportLabels.transit },
        ] as const} selected={[request.transport]} onChange={transport => update({ transport })} />
        <ChoiceGroup title="선호 테마 · 1~3개" options={themeOptions} selected={request.themes} onLayout={event => rememberField('themes', event)}
          error={errors.themes ?? (themeLimitReached ? '테마는 최대 3개까지 선택할 수 있어요.' : undefined)} onChange={theme => {
          if (request.themes.includes(theme)) {
            update({ themes: request.themes.filter(value => value !== theme) });
          } else if (request.themes.length < 3) {
            update({ themes: [...request.themes, theme] });
          } else {
            setThemeLimitReached(true);
            return;
          }
          setThemeLimitReached(false);
        }} />
      </Card>
      <Button secondary title={showOptional ? '선택 조건 접기' : '선택 조건 입력'} onPress={() => setShowOptional(!showOptional)} />
      {showOptional && <Card>
        <ChoiceGroup title="동행 유형" options={companions} selected={request.companion ? [request.companion] : []}
          onChange={value => {
            const companion = request.companion === value ? undefined : value;
            update({ companion, partySize: companion === 'solo' ? 1 : request.partySize === 1 ? undefined : request.partySize });
          }} />
        {request.companion !== 'solo' && <ChoiceGroup title="동행 인원" options={
          (request.companion ? [2, 3, 4, 5] : [1, 2, 3, 4, 5]).map(value => ({
            value: String(value), label: value === 5 ? '5명 이상' : value + '명',
          }))
        } selected={request.partySize ? [String(request.partySize)] : []} onChange={value => {
          update({ partySize: Number(value) === request.partySize ? undefined : Number(value) });
        }} />}
        <ChoiceGroup title="이동 선호도" options={[{ value: 'minimizeWalking', label: '도보 최소화' }] as const}
          selected={request.mobility ? [request.mobility] : []}
          onChange={value => update({ mobility: request.mobility === value ? undefined : value })} />
        <ChoiceGroup title="연령대" options={ages} selected={request.ageGroup ? [request.ageGroup] : []}
          onChange={value => update({ ageGroup: request.ageGroup === value ? undefined : value })} />
      </Card>}
      <Button title="조건 확인" onPress={confirm} />
    </Screen>
  );
}

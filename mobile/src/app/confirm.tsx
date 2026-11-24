import { router } from 'expo-router';

import { startPoints, themes, transportLabels } from '@/course/contracts';
import { minutes } from '@/course/schedule';
import { useCourseStore } from '@/course/store';
import { Button, Card, Copy, Heading, Screen } from '@/course/ui';

export default function Confirm() {
  const input = useCourseStore(state => state.request);
  const origin = startPoints.find(point => point.id === input.startPointId);
  const duration = minutes(input.endTime) - minutes(input.startTime);
  return (
    <Screen title="이 조건으로 추천받을까요?" subtitle="추천에 사용할 일정과 선호 테마를 확인해 주세요.">
      <Card>
        <Heading>{origin?.name} 출발</Heading>
        <Copy>{input.date} · {input.startTime}~{input.endTime} · {duration}분</Copy>
        <Copy>{transportLabels[input.transport]} · {input.themes.map(theme => themes[theme]).join(' · ')}</Copy>
      </Card>
      <Card>
        <Heading>선택 조건</Heading>
        <Copy>동행 유형 · {input.companion ? ({ solo: '혼자', friends: '친구', family: '가족', couple: '연인' })[input.companion] : '미선택'}</Copy>
        <Copy>동행 인원 · {input.partySize ? (input.partySize >= 5 ? '5명 이상' : input.partySize + '명') : '미선택'}</Copy>
        <Copy>이동 선호도 · {input.mobility ? '도보 최소화' : '미정'}</Copy>
        <Copy>연령대 · {input.ageGroup ? (input.ageGroup === '50plus' ? '50대 이상' : input.ageGroup.slice(0, 2) + '대') : '미선택'}</Copy>
      </Card>
      <Button title="코스 추천받기" onPress={() => router.navigate('/recommend')} />
      <Button title="조건 수정" secondary onPress={() => router.dismissTo('/survey')} />
    </Screen>
  );
}

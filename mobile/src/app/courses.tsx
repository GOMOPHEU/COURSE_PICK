import { router } from 'expo-router';

import { isMockMode } from '@/course/api';
import { startPoints, themes } from '@/course/contracts';
import { useCourseStore } from '@/course/store';
import { Button, Card, Copy, Heading, Screen } from '@/course/ui';

export default function Courses() {
  const result = useCourseStore(state => state.result);
  const request = useCourseStore(state => state.request);
  if (!result?.courses.length) {
    return (
      <Screen title={result ? '조건에 맞는 코스가 없어요' : '추천 결과가 없어요'}
        subtitle={result ? '이용 가능 시간을 늘리거나 테마를 바꿔보세요.' : '설문을 작성하면 코스를 볼 수 있어요.'}>
        <Button title="조건 수정" onPress={() => router.dismissTo('/survey')} />
      </Screen>
    );
  }
  return (
    <Screen title="추천 코스" subtitle={isMockMode ? '시연용 데이터 · 이동시간은 추정값입니다.' : '방문 순서와 예상 소요시간을 비교해 보세요.'}>
      <Copy>{startPoints.find(point => point.id === request.startPointId)?.name} 출발 · {request.startTime}~{request.endTime}</Copy>
      {result.courses.map(course => (
        <Card key={course.courseId}>
          <Heading>{course.places.map(place => place.name).join(' → ')}</Heading>
          <Copy>{course.places.length}곳 · {[...new Set(course.places.map(place => themes[place.theme]))].join(' · ')}</Copy>
          <Copy>{course.totalMinutes}분 · {course.endAt} 종료 · {course.fit === 'relaxed' ? '시간 여유' : '시간 빠듯함'}</Copy>
          {!isMockMode && course.source === 'basic' && <Copy>규칙 기반 추천</Copy>}
          <Copy>{course.reason}</Copy>
          <Button title="시간표 보기" onPress={() => router.push({
            pathname: '/course/[courseId]', params: { courseId: course.courseId },
          })} />
        </Card>
      ))}
      <Button title="조건 수정" secondary onPress={() => router.dismissTo('/survey')} />
    </Screen>
  );
}

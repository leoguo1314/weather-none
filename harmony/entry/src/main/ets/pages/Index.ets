import { http } from '@kit.NetworkKit';

class CityOption {
  name: string;
  latitude: number;
  longitude: number;

  constructor(name: string, latitude: number, longitude: number) {
    this.name = name;
    this.latitude = latitude;
    this.longitude = longitude;
  }
}

class ForecastDay {
  date: string;
  weatherCode: number;
  high: number;
  low: number;
  precipitationProbability: number;

  constructor(date: string, weatherCode: number, high: number, low: number,
    precipitationProbability: number) {
    this.date = date;
    this.weatherCode = weatherCode;
    this.high = high;
    this.low = low;
    this.precipitationProbability = precipitationProbability;
  }
}

interface OpenMeteoCurrent {
  temperature_2m: number;
  relative_humidity_2m: number;
  apparent_temperature: number;
  precipitation: number;
  weather_code: number;
  pressure_msl: number;
  wind_speed_10m: number;
  wind_direction_10m: number;
}

interface OpenMeteoDaily {
  time: string[];
  weather_code: number[];
  temperature_2m_max: number[];
  temperature_2m_min: number[];
  precipitation_probability_max: number[];
}

interface OpenMeteoResponse {
  current: OpenMeteoCurrent;
  daily: OpenMeteoDaily;
}

const CITIES: CityOption[] = [
  new CityOption('北京', 39.9042, 116.4074),
  new CityOption('上海', 31.2304, 121.4737),
  new CityOption('深圳', 22.5431, 114.0579),
  new CityOption('广州', 23.1291, 113.2644),
  new CityOption('新加坡', 1.3521, 103.8198)
];

function weatherText(code: number): string {
  if (code === 0) {
    return '晴';
  }
  if (code === 1 || code === 2) {
    return '晴间多云';
  }
  if (code === 3) {
    return '阴';
  }
  if (code === 45 || code === 48) {
    return '雾';
  }
  if (code >= 51 && code <= 57) {
    return '毛毛雨';
  }
  if (code >= 61 && code <= 67) {
    return '降雨';
  }
  if (code >= 71 && code <= 77) {
    return '降雪';
  }
  if (code >= 80 && code <= 82) {
    return '阵雨';
  }
  if (code >= 85 && code <= 86) {
    return '阵雪';
  }
  if (code >= 95) {
    return '雷暴';
  }
  return '天气变化';
}

function weatherIcon(code: number): string {
  if (code === 0) {
    return '☀️';
  }
  if (code <= 3) {
    return '⛅';
  }
  if (code === 45 || code === 48) {
    return '🌫️';
  }
  if (code >= 71 && code <= 77 || code >= 85 && code <= 86) {
    return '🌨️';
  }
  if (code >= 95) {
    return '⛈️';
  }
  if (code >= 51 && code <= 82) {
    return '🌧️';
  }
  return '🌤️';
}

function directionText(degree: number): string {
  const directions: string[] = ['北', '东北', '东', '东南', '南', '西南', '西', '西北'];
  const index: number = Math.round(degree / 45) % 8;
  return directions[index];
}

@Entry
@Component
struct Index {
  @State cityIndex: number = 0;
  @State activeTab: number = 0;
  @State loading: boolean = true;
  @State loadError: string = '';
  @State temperature: number = 0;
  @State apparentTemperature: number = 0;
  @State humidity: number = 0;
  @State precipitation: number = 0;
  @State weatherCode: number = 0;
  @State pressure: number = 0;
  @State windSpeed: number = 0;
  @State windDirection: number = 0;
  @State forecast: ForecastDay[] = [];
  @State question: string = '';
  @State agentAnswer: string = '我会根据当前实时天气，给出穿衣、带伞和出行建议。';
  @State toolTrace: string[] = [];
  private requestSerial: number = 0;

  aboutToAppear(): void {
    this.loadWeather();
  }

  private currentCity(): CityOption {
    return CITIES[this.cityIndex];
  }

  private async loadWeather(): Promise<void> {
    const requestSerial: number = ++this.requestSerial;
    this.loading = true;
    this.loadError = '';
    const city: CityOption = this.currentCity();
    const url: string = 'https://api.open-meteo.com/v1/forecast' +
      `?latitude=${city.latitude}&longitude=${city.longitude}` +
      '&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,' +
      'weather_code,pressure_msl,wind_speed_10m,wind_direction_10m' +
      '&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max' +
      '&forecast_days=7&timezone=auto';
    const request: http.HttpRequest = http.createHttp();
    try {
      const response: http.HttpResponse = await request.request(url, {
        method: http.RequestMethod.GET,
        connectTimeout: 15000,
        readTimeout: 15000,
        expectDataType: http.HttpDataType.STRING
      });
      if (response.responseCode !== 200) {
        throw new Error(`天气服务返回 ${response.responseCode}`);
      }
      const raw: string = response.result as string;
      const data: OpenMeteoResponse = JSON.parse(raw) as OpenMeteoResponse;
      if (requestSerial !== this.requestSerial) {
        return;
      }
      this.temperature = data.current.temperature_2m;
      this.apparentTemperature = data.current.apparent_temperature;
      this.humidity = data.current.relative_humidity_2m;
      this.precipitation = data.current.precipitation;
      this.weatherCode = data.current.weather_code;
      this.pressure = data.current.pressure_msl;
      this.windSpeed = data.current.wind_speed_10m;
      this.windDirection = data.current.wind_direction_10m;

      const days: ForecastDay[] = [];
      const count: number = Math.min(7, data.daily.time.length);
      for (let index: number = 0; index < count; index++) {
        days.push(new ForecastDay(
          data.daily.time[index],
          data.daily.weather_code[index],
          data.daily.temperature_2m_max[index],
          data.daily.temperature_2m_min[index],
          data.daily.precipitation_probability_max[index]
        ));
      }
      this.forecast = days;
      this.agentAnswer = `${city.name}天气已更新。你可以问我未来趋势、穿衣、带伞或户外活动风险。`;
    } catch (error) {
      if (requestSerial === this.requestSerial) {
        this.loadError = `加载失败：${error instanceof Error ? error.message : '网络异常'}`;
      }
    } finally {
      request.destroy();
      if (requestSerial === this.requestSerial) {
        this.loading = false;
      }
    }
  }

  private selectCity(index: number): void {
    if (index === this.cityIndex) {
      return;
    }
    this.cityIndex = index;
    this.forecast = [];
    this.toolTrace = [];
    this.question = '';
    this.agentAnswer = `正在加载 ${this.currentCity().name} 的实时天气…`;
    this.loadWeather();
  }

  private askAgent(preset?: string): void {
    if (this.loading || this.loadError.length > 0) {
      this.toolTrace = [];
      this.agentAnswer = '天气数据尚未就绪，请先完成刷新后再提问。';
      return;
    }
    const input: string = (preset ?? this.question).trim();
    if (input.length === 0) {
      return;
    }
    if (preset !== undefined) {
      this.question = preset;
    }

    const traces: string[] = ['weather.query'];
    const lower: string = input.toLowerCase();
    if (lower.includes('明天') || lower.includes('未来') || lower.includes('趋势') || lower.includes('一周')) {
      traces.push('forecast.query');
    }
    if (lower.includes('穿') || lower.includes('衣') || lower.includes('带伞') || lower.includes('防晒')) {
      traces.push('clothing.advice');
    }
    if (lower.includes('出门') || lower.includes('出行') || lower.includes('跑步') || lower.includes('户外')) {
      traces.push('travel.advice');
    }

    const advice: string[] = [];
    advice.push(`${this.currentCity().name}当前${weatherText(this.weatherCode)}，${Math.round(this.temperature)}℃，` +
      `体感${Math.round(this.apparentTemperature)}℃，湿度${Math.round(this.humidity)}%。`);

    if (this.apparentTemperature <= 8) {
      advice.push('体感较冷，建议厚外套并注意保暖。');
    } else if (this.apparentTemperature <= 18) {
      advice.push('建议长袖搭配薄外套，早晚注意温差。');
    } else if (this.apparentTemperature >= 32) {
      advice.push('体感炎热，建议轻薄速干衣物、补水并减少正午暴晒。');
    } else {
      advice.push('体感舒适，轻薄衣物即可。');
    }

    const firstDay: ForecastDay | undefined = this.forecast.length > 0 ? this.forecast[0] : undefined;
    const rainLikely: boolean = this.precipitation > 0 ||
      this.weatherCode >= 51 && this.weatherCode <= 82 ||
      firstDay !== undefined && firstDay.precipitationProbability >= 40;
    if (rainLikely) {
      advice.push('有降水可能，建议带伞、穿防滑鞋并预留通勤时间。');
    } else {
      advice.push('目前降水风险较低，可正常安排通勤和户外活动。');
    }

    if (this.windSpeed >= 30) {
      advice.push('风力较强，避免在广告牌、临时搭建物和大树附近久留。');
    }

    if (traces.includes('forecast.query') && this.forecast.length > 1) {
      const trend: string = this.forecast.slice(0, 4).map((day: ForecastDay) =>
        `${day.date.substring(5)} ${weatherText(day.weatherCode)} ${Math.round(day.low)}~${Math.round(day.high)}℃`
      ).join('；');
      advice.push(`未来趋势：${trend}。`);
    }

    this.toolTrace = traces;
    this.agentAnswer = advice.join('\n');
  }

  @Builder
  private tabHeader(): void {
    Row({ space: 10 }) {
      Button('天气')
        .layoutWeight(1)
        .height(42)
        .fontColor(this.activeTab === 0 ? '#153B70' : '#FFFFFF')
        .backgroundColor(this.activeTab === 0 ? '#FFFFFF' : '#33FFFFFF')
        .onClick(() => this.activeTab = 0)
      Button('AI 助手')
        .layoutWeight(1)
        .height(42)
        .fontColor(this.activeTab === 1 ? '#153B70' : '#FFFFFF')
        .backgroundColor(this.activeTab === 1 ? '#FFFFFF' : '#33FFFFFF')
        .onClick(() => this.activeTab = 1)
    }
    .width('100%')
  }

  @Builder
  private citySelector(): void {
    Scroll() {
      Row({ space: 8 }) {
        ForEach(CITIES, (city: CityOption, index: number) => {
          Button(city.name)
            .fontSize(14)
            .height(36)
            .fontColor(index === this.cityIndex ? '#163B6D' : '#FFFFFF')
            .backgroundColor(index === this.cityIndex ? '#FFFFFF' : '#26FFFFFF')
            .onClick(() => this.selectCity(index))
        }, (city: CityOption) => city.name)
      }
    }
    .scrollable(ScrollDirection.Horizontal)
    .width('100%')
  }

  @Builder
  private weatherPage(): void {
    Scroll() {
      Column({ space: 14 }) {
        this.citySelector()

        if (this.loading) {
          Column({ space: 14 }) {
            LoadingProgress().width(42).height(42).color('#FFFFFF')
            Text('正在获取实时天气…').fontColor('#FFFFFF')
          }
          .width('100%')
          .height(220)
          .justifyContent(FlexAlign.Center)
        } else if (this.loadError.length > 0) {
          Column({ space: 14 }) {
            Text(this.loadError).fontColor('#FFF1F1').fontSize(16)
            Button('重新加载').onClick(() => this.loadWeather())
          }
          .width('100%')
          .height(220)
          .justifyContent(FlexAlign.Center)
        } else {
          Column({ space: 8 }) {
            Text(this.currentCity().name)
              .fontSize(26)
              .fontWeight(FontWeight.Bold)
              .fontColor('#FFFFFF')
            Text(`${weatherIcon(this.weatherCode)} ${weatherText(this.weatherCode)}`)
              .fontSize(22)
              .fontColor('#FFFFFF')
            Text(`${Math.round(this.temperature)}°`)
              .fontSize(82)
          .fontWeight(FontWeight.Lighter)
              .fontColor('#FFFFFF')
            Text(`体感 ${Math.round(this.apparentTemperature)}℃`)
              .fontSize(16)
              .fontColor('#DDEEFF')
          }
          .width('100%')
          .padding(22)
          .backgroundColor('#20FFFFFF')
          .borderRadius(26)

          Row({ space: 10 }) {
            this.metricCard('湿度', `${Math.round(this.humidity)}%`)
            this.metricCard('风速', `${Math.round(this.windSpeed)} km/h`)
          }
          Row({ space: 10 }) {
            this.metricCard('风向', `${directionText(this.windDirection)}风`)
            this.metricCard('气压', `${Math.round(this.pressure)} hPa`)
          }

          Column({ space: 4 }) {
            Text('7 日预报')
              .fontSize(19)
              .fontWeight(FontWeight.Bold)
              .fontColor('#17385F')
              .margin({ bottom: 8 })
            ForEach(this.forecast, (day: ForecastDay, index: number) => {
              Row() {
                Text(index === 0 ? '今天' : day.date.substring(5))
                  .width(70)
                  .fontColor('#233B5A')
                Text(`${weatherIcon(day.weatherCode)} ${weatherText(day.weatherCode)}`)
                  .layoutWeight(1)
                  .fontColor('#233B5A')
                Text(`${Math.round(day.low)}° / ${Math.round(day.high)}°`)
                  .fontColor('#233B5A')
                Text(` ${Math.round(day.precipitationProbability)}%`)
                  .width(52)
                  .textAlign(TextAlign.End)
                  .fontColor('#4E6B8F')
              }
              .width('100%')
              .height(45)
              .alignItems(VerticalAlign.Center)
            }, (day: ForecastDay) => day.date)
          }
          .width('100%')
          .padding(18)
          .backgroundColor('#F2FFFFFF')
          .borderRadius(24)
        }
      }
      .width('100%')
      .padding({ bottom: 28 })
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private metricCard(label: string, value: string): void {
    Column({ space: 5 }) {
      Text(label).fontSize(13).fontColor('#DDEEFF')
      Text(value).fontSize(18).fontWeight(FontWeight.Medium).fontColor('#FFFFFF')
    }
    .layoutWeight(1)
    .padding(15)
    .backgroundColor('#24FFFFFF')
    .borderRadius(18)
  }

  @Builder
  private agentPage(): void {
    Scroll() {
      Column({ space: 14 }) {
        Column({ space: 8 }) {
          Text('SkyPulse Weather Agent')
            .fontSize(22)
            .fontWeight(FontWeight.Bold)
            .fontColor('#FFFFFF')
          Text(`正在使用 ${this.currentCity().name} 的实时天气工具`)
            .fontSize(14)
            .fontColor('#DDEEFF')
        }
        .width('100%')

        Column({ space: 8 }) {
          Text('快捷问题').fontSize(16).fontWeight(FontWeight.Bold).fontColor('#17385F')
          this.suggestionButton('明天适合户外跑步吗？')
          this.suggestionButton('今天怎么穿，需要带伞吗？')
          this.suggestionButton('未来四天天气趋势')
        }
        .width('100%')
        .padding(16)
        .backgroundColor('#F0FFFFFF')
        .borderRadius(22)

        Column({ space: 10 }) {
          Text('AI 分析').fontSize(16).fontWeight(FontWeight.Bold).fontColor('#17385F')
          Text(this.agentAnswer)
            .fontSize(16)
            .lineHeight(25)
            .fontColor('#213B5A')
          if (this.toolTrace.length > 0) {
            Text(`已调用：${this.toolTrace.join(' → ')}`)
              .fontSize(12)
              .fontColor('#55718F')
              .margin({ top: 4 })
          }
        }
        .width('100%')
        .padding(18)
        .backgroundColor('#F5FFFFFF')
        .borderRadius(22)

        Row({ space: 8 }) {
          TextInput({ text: this.question, placeholder: '问天气、穿衣或出行风险' })
            .layoutWeight(1)
            .height(48)
            .backgroundColor('#F5FFFFFF')
            .borderRadius(16)
            .onChange((value: string) => this.question = value)
          Button('发送')
            .height(48)
            .backgroundColor('#1768C4')
            .onClick(() => this.askAgent())
        }
        .width('100%')
      }
      .width('100%')
      .padding({ bottom: 28 })
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private suggestionButton(text: string): void {
    Button(text)
      .width('100%')
      .height(42)
      .fontSize(14)
      .fontColor('#21507C')
      .backgroundColor('#E8F2FF')
      .onClick(() => this.askAgent(text))
  }

  build() {
    Column({ space: 14 }) {
      Row() {
        Column({ space: 2 }) {
          Text('SkyPulse')
            .fontSize(24)
            .fontWeight(FontWeight.Bold)
            .fontColor('#FFFFFF')
          Text('AI 天气 · HarmonyOS')
            .fontSize(13)
            .fontColor('#DDEEFF')
        }
        .layoutWeight(1)
          Button('刷新')
            .height(34)
            .fontSize(12)
            .fontColor('#FFFFFF')
            .backgroundColor('#26FFFFFF')
            .enabled(!this.loading)
            .onClick(() => this.loadWeather())
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)

      this.tabHeader()

      if (this.activeTab === 0) {
        this.weatherPage()
      } else {
        this.agentPage()
      }
    }
    .width('100%')
    .height('100%')
    .padding({ top: 18, left: 16, right: 16 })
    .linearGradient({
      angle: 180,
      colors: [['#123E75', 0.0], ['#3F83BD', 0.55], ['#96CDEB', 1.0]]
    })
  }
}

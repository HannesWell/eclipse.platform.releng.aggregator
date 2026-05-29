import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeTests {

	// TODO: Delete this!
	public static void main(String[] args) {
		LocalDate date = LocalDate.parse("2026-05-11");
		LocalDateTime time = LocalDate.now().plusDays(1).atTime(18, 0);
		ZonedDateTime atZone3 = time.atZone(ZoneId.of("America/Toronto"));

		//		LocalDateTime time = date.atTime(18, 0);

		System.out.println("Zone");
		ZonedDateTime atZone = time.atZone(ZoneId.of("America/Toronto"));
		System.out.println(atZone.getZone());
		System.out.println(DateTimeFormatter.ISO_INSTANT.format(atZone));
		System.out.println(DateTimeFormatter.BASIC_ISO_DATE.format(atZone));
		System.out.println(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(atZone));
		System.out.println(atZone);
		System.out.println(DateTimeFormatter.ISO_LOCAL_DATE.format(atZone));

		System.out.println("Offset");
		OffsetDateTime atOffset = atZone.toOffsetDateTime();
		System.out.println(DateTimeFormatter.ISO_INSTANT.format(atOffset));
		System.out.println(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(atOffset));

		System.out.println("UTC");
		ZonedDateTime atZone2 = time.atZone(ZoneOffset.UTC);
		System.out.println(DateTimeFormatter.ISO_INSTANT.format(atZone2));
		System.out.println(DateTimeFormatter.ISO_DATE_TIME.format(atZone2));
		System.out.println(atZone2);
	}
}

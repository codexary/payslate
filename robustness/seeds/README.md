# Synthetic seed files

- `plain.csv`: comma export with unquoted names and 24-hour times.
- `bom-semicolon.csv`: semicolon export with a UTF-8 BOM, CRLF and holiday values.
- `tab-twelve.csv`: tab export with 12-hour times, including an overnight shift.
- `ticket-a-quoted.csv`: support case A, a rostering export with every field quoted across several rows.
- `ticket-b-stray-quote.csv`: support case B, a hand edit with one stray double quote in a name.
- `ticket-c-windows1252.csv`: support case C, Windows-1252 text with byte `0xE9` in Renée Dubois.

All shifts fall within 6–12 July 2026. Names and shifts are synthetic.

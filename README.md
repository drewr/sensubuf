# sensubuf

You know how sensu-client listens on port 3030 UDP?  You didn't?
Well, you can send checks to that!

## Usage

Pick your check format du jour, like so:

    {"name":"checkplease","type":"metric","output":"%%BATCH%%","status":0,"handler":"checkyoself"}

And give it a `stubtok` like `%%BATCH%%`.  The file contents that are
new since last run (compared against `--offset-file`) will be split up
by `--batch` lines and substituted in there.

    % TMPL='{"name":"checkplease","type":"metric","output":"%%BATCH%%","status":0,"handler":"checkyoself"}'
    % java -server -jar sensubuf.jar --offset-file /tmp/checkplease.offset --file /var/log/system -v -t $TMPL

## License

Copyright © 2012 Andrew A. Raines

Distributed under the MIT License.

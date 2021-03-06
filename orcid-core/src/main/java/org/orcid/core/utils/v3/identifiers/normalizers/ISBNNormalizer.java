package org.orcid.core.utils.v3.identifiers.normalizers;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

@Component
public class ISBNNormalizer implements Normalizer{

    private static final List<String> canHandle = Lists.newArrayList("isbn");
    
    //This sometimes gives false matches for number strings between 10-17 chars long.  
    //The code deals with these false matches by ignoring ISBNs that are not 10 or 13 chars long.
    //The other solution is a super complicated regex to deal with it.
    private static final Pattern pattern = Pattern.compile("([0-9][-0-9 ]{8,15}[0-9xX])(?:$|[^0-9])");
    
    @Override
    public List<String> canHandle() {
        return canHandle;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public String normalise(String apiTypeName, String value) {
        if (!canHandle.contains(apiTypeName))
            return value;
        Matcher m = pattern.matcher(value);
        if (m.find()){
            String n = m.group(1);
            if (n != null){
                n = n.replace("-", "");
                n = n.replace(" ", "");
                n = n.replace("x", "X");
                if (n.length() == 10 || n.length() == 13)
                    return n;
            }
        }
        return "";
    }

}
